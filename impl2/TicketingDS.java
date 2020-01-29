package ticketingsystem.impl2;

import ticketingsystem.Ticket;
import ticketingsystem.TicketingSystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketingDS implements TicketingSystem {
    //有效票集合
    private static final Map<Long, Ticket> validTickets = new ConcurrentHashMap<>(7300000,1);
    //记录了当前线程买票的次数
    private static final ThreadLocal<Integer> cnt = ThreadLocal.withInitial(() -> 1);
    public Route[] routes;

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        routes = new Route[routenum];
        for (int i = 0; i < routenum; i++) {
            routes[i] = new Route(stationnum, coachnum, seatnum);
        }
    }

    /**
     * refundTicket是退票方法，对有效的Ticket对象返回true，对错误或无效的Ticket对象返回false
     */
    @Override
    public boolean refundTicket(Ticket ticket) {
        if (ticket == null)
            return false;
        Ticket t = validTickets.get(ticket.tid);
        if (t == null) {
            return false;
        } else {
            if (isSame(t, ticket)) {
                if (validTickets.remove(ticket.tid) != null) {
                    Route r = routes[ticket.route-1];
                    Coach c = r.coaches[ticket.coach-1];
                    Seat s = c.seats[ticket.seat-1];
                    //TTAS 抢座位锁
                    while (true) {
                        while (s.occupying.get()); //wait
                        if (s.occupying.compareAndSet(false, true))
                            //success
                            break;
                    }
                    s.release(ticket.departure, ticket.arrival); //释放座位
                    merge(r, c, s, ticket.departure, ticket.arrival);
                    s.occupying.set(false); //释放锁
                    return true;
                } else  {
                    //有人抢先把这个票给退了
                    return false;
                }
            } else {
                //同一个tid, 但有的域不同, 是无效票
                return false;
            }
        }
    }

    //比较票的各个域是否相等
    private boolean isSame(Ticket t1, Ticket t2) {
        return t1.passenger.equals(t2.passenger) && t1.route == t2.route &&
                t1.coach == t2.coach && t1.seat == t2.seat &&
                t1.departure == t2.departure && t1.arrival == t2.arrival;
    }


    //将票拆开
    private void spilt(Route r, Coach c, Seat s, int departure, int arrival) {
        int res = getMaxIdleInterval(s, departure, arrival, r.stationNum);
        int from = res / 100, to = res % 100;
        r.left[from][to].getAndDecrement();
        c.left[from][to].getAndDecrement();
        if (from < departure) {
            r.left[from][departure].getAndIncrement();
            c.left[from][departure].getAndIncrement();
        }
        if (arrival < to) {
            r.left[arrival][to].getAndIncrement();
            c.left[arrival][to].getAndIncrement();
        }
    }

    //将票合并
    private void merge(Route r, Coach c, Seat s, int departure, int arrival) {
        int res = getMaxIdleInterval(s, departure, arrival, r.stationNum);
        int from = res / 100, to = res % 100;
        r.left[from][to].getAndIncrement();
        c.left[from][to].getAndIncrement();
        if (from < departure) {
            r.left[from][departure].getAndDecrement();
            c.left[from][departure].getAndDecrement();
        }
        if (arrival < to) {
            r.left[arrival][to].getAndDecrement();
            c.left[arrival][to].getAndDecrement();
        }
    }

    //从[departure, arrival]左右扩散, 看该座位空闲的最大区间段
    private int getMaxIdleInterval(Seat s, int departure, int arrival, int stationNum) {
        int from = departure - 1, to = arrival;
        while (from >= 1) {
            if (s.isOccupied(from)) {
                break;
            }
            from--;
        }
        //比如是idle[2]break出来, 那么代表2-3不是空的, 所以from = 2 + 1
        //正常循环退出来的, 那么此时from = 0, 所以from = 0 + 1
        //故都需要from++
        from++;
        while (to < stationNum) {
            if (s.isOccupied(to)) {
                break;
            }
            to++;
        }
        //比如是idle[7]break出来的, 那么代表7-8不是空的, 此时to = 7没错
        //正常循环退出来的, 那么此时to = stationNum, 也同样正确
        return from * 100 + to;
    }

    /**
     * inquiry是查询余票方法，即查询route车次从departure站到arrival站的余票数。
     */
    @Override
    public int inquiry(int routeID, int departure, int arrival) {
        return inquiry(routes[routeID-1].left, departure, arrival);
    }

    //给定一班列车或一个车厢的剩余座位数组, 查询目标区间还剩多少个座位
    private int inquiry(AtomicInteger[][] left,int departure, int arrival) {
        int stationNum = left.length-1, res = 0;
        for (int i = 1; i <= departure; i++) {
            for (int j = arrival; j <= stationNum; j++)
                res += left[i][j].get();
        }
        return res;
    }

    /**
     * buyTicket是购票方法，即乘客passenger购买route车次从departure站到arrival站的车票1张。
     * 若购票成功，返回有效的Ticket对象；
     * 若失败 （即无余票），返回无效的Ticket对象（即return null）。
     */
    @Override
    public Ticket buyTicket(String passenger, int routeID, int departure, int arrival) {
        Route route = routes[routeID-1];
        for (int cur = 0; cur < route.coachNum; cur++) {
            Coach coach = route.coaches[cur];
            //先查一下这个车厢还有没有座位, 没了就略过
            if (inquiry(coach.left, departure, arrival) > 0) {
                for (int now = 0; now < coach.seatNum; now++) {
                    Seat seat = coach.seats[now];
                    //获取座位锁
                    if (seat.occupying.compareAndSet(false, true)) {
                        boolean hasFound = true;
                        for (int i = departure; i < arrival; i++) {
                            if (seat.isOccupied(i)) {
                                hasFound = false;
                                break;
                            }
                        }
                        if (hasFound) {
                            seat.take(departure, arrival);
                            spilt(route, coach, seat, departure, arrival);
                            //释放座位锁
                            seat.occupying.set(false);
                            return ticketGenerator(passenger, routeID, departure, arrival, cur, now);
                        } else {
                            //当前座位在目标区间内不全空闲, 释放座位锁
                            seat.occupying.set(false);
                        }
                    }
                }
            }
        }
        return null;
    }

    private Ticket ticketGenerator(String passenger, int routeID, int departure,
                                   int arrival, int coach, int seat) {
        Ticket ticket = new Ticket();
        int prev = cnt.get();
        cnt.set(prev + 1);
        long tid = getTid(prev);
        ticket.tid = tid;
        ticket.passenger = passenger;
        ticket.route = routeID;
        ticket.coach = coach + 1;
        ticket.seat = seat + 1;
        ticket.departure = departure;
        ticket.arrival = arrival;
        validTickets.put(tid, ticket);
        return ticket;
    }

    private long getTid(int n) {
        long threadID = Thread.currentThread().getId();
        int tmp = n;
        while (tmp >= 1) {
            tmp /= 10;
            threadID *= 10;
        }
        return threadID + n;
    }
}

