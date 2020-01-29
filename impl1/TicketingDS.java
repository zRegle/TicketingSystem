package ticketingsystem.impl1;

import ticketingsystem.Singleton;
import ticketingsystem.Ticket;
import ticketingsystem.TicketingSystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TicketingDS implements TicketingSystem {
    private Route[] routes;
    private static AtomicLong tidGenerator = new AtomicLong(1);
    private static ConcurrentHashMap<Long, Boolean> tickets = new ConcurrentHashMap<>();

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        routes = new Route[routenum + 1];
        for (int i = 1; i <= routenum; i++) {
            routes[i] = new Route(1, stationnum, i, coachnum, seatnum);
        }
    }

    @Override
    public Ticket buyTicket(String passenger, int routeID, int departure, int arrival) {
        Route route = routes[routeID];
        State[][] states = route.states;
        ConcurrentHashMap<Integer, Boolean> target = states[departure][arrival].availableSeats;
        for (Map.Entry<Integer, Boolean> entry : target.entrySet()) {
            int seat = entry.getKey();
            if (target.get(seat) == null)
                //在foreach循环中, entrySet是一份拷贝
                //也就是说当前循环的键值对可能已经不在map真正的entrySet里
                //所以这里要先判断一下是否在当前map里, 如果不在就不要浪费时间去循环了
                continue;
            /* 从各个区间跟目标区间有交集的区间的map中占领该座位
             * 比如想一共有8个站, 占领3->6的1号座位
             * 那么
             * 1->4, 1->5, 1->6, 1->7, 1->8
             * 2->4, 2->5, 2->6, 2->7, 2->8
             * 3->4, 3->5, 3->6, 3->7, 3->8
             * 4->5, 4->6, 4->7, 4->8
             * 5->6, 5->7, 5->8
             * 以上这些区间的1号座位都要尝试去占领
             */
            boolean flag = true;
            Try: for (int i = 1; i < arrival; i++) {
                for (int j = Math.max(departure, i) + 1; j <= route.stationNum; j++) {
                    State s = states[i][j];
                    ConcurrentHashMap<Integer, Boolean> map = s.availableSeats;
                    if (map.remove(seat) == null) {
                        //目标座位已经被其他线程从这个map中删除了
                        flag = false;
                        //复原之前区间的状态
                        recovery(routeID, departure, arrival, seat, i, j);
                        break Try;  //换一个座位重新尝试
                    } else {
                        //占座成功
                        int prev = s.left.getAndDecrement();
                        //有可能这里会出现prev为0的情况
                        //因为可能有的线程将座位put到map里了, 但还没有把left++
                        if (prev < 0) {
                            Singleton.getInstance().errorMsg(
                                    "\n*******************************" +
                                    "\nseat less than 0: " +
                                    "\nrouteID: " + routeID +
                                    "\ncount: " + prev +
                                    "\nfrom: " + i + " to: " + j);
                        }
                    }
                }
            }
            if (flag) {
                return ticketGenerator(passenger, routeID, departure, arrival, seat);
            }
        }
        //所有座位都尝试过,但是都失败了, 表示购票失败
        return null;
    }

    /**
     * 复原从departure到arrival之间所有区间目标座位的状态
     * @param routeID 列车ID
     * @param departure 始发站
     * @param arrival 终点站
     * @param targetSeat 目标座位
     * @param failedStart 如果是占座调用的, 则表示占座失败区间的始发站; 如果是退票调用的则为-1
     * @param failedEnd 如果是占座调用的, 则表示占座失败区间的终点站; 如果是退票调用的则为-1
     */
    private void recovery(int routeID, int departure, int arrival,
                          int targetSeat, int failedStart, int failedEnd) {
        Route route = routes[routeID];
        State[][] states = route.states;
        for (int i = 1; i < arrival; i++) {
            for (int j = Math.max(departure, i) + 1; j <= route.stationNum; j++) {
                State s = states[i][j];
                ConcurrentHashMap<Integer, Boolean> map = s.availableSeats;
                if (i == failedStart && j == failedEnd)
                    //占座调用的recovery, 失败区间之前的区间全部复原完毕, 直接退出
                    return;
                Boolean flag = map.put(targetSeat, true);
                if (flag != null) {
                    //不知道为啥被删除的座位会出现这个map里
                    Singleton.getInstance().errorMsg(
                            "\n*******************************" +
                            "\nseat should not appear: " +
                            "\nrouteID: " + routeID +
                            "\nseat: " + targetSeat +
                            "\nfrom: " + i + " to: " + j +
                            "\nstate: " + flag);
                }
                int prev = s.left.getAndIncrement();
                //prev有可能等于total seat
                //因为可能有的线程将座位remove了, 但还没有把left--
                if (prev > route.total_seat) {
                    Singleton.getInstance().errorMsg(
                            "\n*******************************" +
                            "\nseat greater than seatNum: " +
                            "\nrouteID: " + routeID +
                            "\ncount: " + prev + ", limit: " + route.total_seat +
                            "\nfrom: " + i + " to: " + j);
                }
            }
        }
    }

    @Override
    public int inquiry(int routeID, int departure, int arrival) {
        Route route = routes[routeID];
        State s = route.states[departure][arrival];
        return s.left.get();
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        if (ticket == null) {
            return false;
        } else {
            //判断是否重复退同一张票
            boolean isSuccessful = tickets.remove(ticket.tid, true);
            if (isSuccessful) {
                //恢复相应区间的座位空闲情况, 剩余座位数
                int departure = ticket.departure, arrival = ticket.arrival;
                int routeID = ticket.route;
                Route route = routes[routeID];
                //需要判断是否为车厢最后一个座位, 才能算出正确的idx值
                int seatIdx;
                if (ticket.seat == route.seatNum) //最后一个座位
                    seatIdx = ticket.coach * route.seatNum;
                else //不是最后一个座位
                    seatIdx = (ticket.coach-1) * route.seatNum + ticket.seat;
                recovery(routeID, departure, arrival, seatIdx, -1, -1);
            }
            return isSuccessful;
        }
    }

    private Ticket ticketGenerator(String passenger, int routeID, int departure, int arrival, int seatIdx) {
        Route route = routes[routeID];
        long tid = tidGenerator.getAndIncrement();
        //座位标号从1开始计数, 要判断是否为车厢的最后一个座位
        int tmp = seatIdx % route.seatNum;
        Ticket ticket = new Ticket();
        ticket.tid = tid;
        ticket.passenger = passenger;
        ticket.route = routeID;
        ticket.coach = seatIdx / route.seatNum + (tmp == 0 ? 0 : 1);
        ticket.seat = tmp == 0 ? route.seatNum : tmp;
        ticket.departure = departure;
        ticket.arrival = arrival;
        tickets.put(tid, true);
        return ticket;
    }
}