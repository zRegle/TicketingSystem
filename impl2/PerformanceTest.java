package ticketingsystem.impl2;

import ticketingsystem.Ticket;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.LongAdder;

class PerformanceData {
    LongAdder methodCnt;
    LongAdder totalTime;

    PerformanceData() {
        methodCnt = new LongAdder();
        totalTime = new LongAdder();
    }
}

public class PerformanceTest {
    private final static int routenum = 20; // route is designed from 1 to 3
    private final static int coachnum = 15; // coach is arranged from 1 to 5
    private final static int seatnum = 100; // seat is allocated from 1 to 20
    private final static int stationnum = 10; // station is designed from 1 to 5
    private final static int testnum = 500000;
    private final static int retpc = 10; // return ticket operation is 10% percent
    private final static int buypc = 40; // buy ticket operation is 30% percent
    private final static int inqpc = 100; //inquiry ticket operation is 60% percent
    private static final PerformanceData inquiry = new PerformanceData();
    private static final PerformanceData buy = new PerformanceData();
    private static final PerformanceData refund = new PerformanceData();


    static String passengerName() {
        Random rand = new Random();
        long uid = rand.nextInt(testnum);
        return "passenger" + uid;
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCnt = Integer.parseInt(args[0]);
        Thread[] threads = new Thread[threadCnt];
        final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadCnt);
        long begin = System.nanoTime();
        for (int i = 0; i< threadCnt; i++) {
            threads[i] = new Thread(() -> {
                Random rand = new Random();
                Ticket ticket;
                ArrayList<Ticket> soldTicket = new ArrayList<>();
                for (int j = 0; j < testnum; j++) {
                    int sel = rand.nextInt(inqpc);
                    if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
                        int select = rand.nextInt(soldTicket.size());
                        if ((ticket = soldTicket.remove(select)) != null) {
                            refund.methodCnt.increment();
                            long start = System.nanoTime();
                            if (tds.refundTicket(ticket)) {
                                //Singleton.getInstance().logMsg("TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach  + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                            } else {
                                System.out.println("ErrOfRefund");
                                System.out.flush();
                            }
                            long finish = System.nanoTime();
                            refund.totalTime.add(finish - start);
                        } else {
                            System.out.println("ErrOfRefund");
                            System.out.flush();
                        }
                    } else if (retpc <= sel && sel < buypc) { // buy ticket
                        String passenger = passengerName();
                        int route = rand.nextInt(routenum) + 1;
                        int departure = rand.nextInt(stationnum - 1) + 1;
                        int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                        buy.methodCnt.increment();
                        long start = System.nanoTime();
                        if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
                            soldTicket.add(ticket);
                            //Singleton.getInstance().logMsg("TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                        } else {
                            //Singleton.getInstance().logMsg("TicketSoldOut" + " " + route+ " " + departure+ " " + arrival);
                        }
                        long finish = System.nanoTime();
                        buy.totalTime.add(finish - start);
                    } else if (buypc <= sel && sel < inqpc) { // inquiry ticket
                        int route = rand.nextInt(routenum) + 1;
                        int departure = rand.nextInt(stationnum - 1) + 1;
                        int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                        inquiry.methodCnt.increment();
                        long start = System.nanoTime();
                        int leftTicket = tds.inquiry(route, departure, arrival);
                        //Singleton.getInstance().logMsg("RemainTicket" + " " + leftTicket + " " + route+ " " + departure+ " " + arrival);
                        long finish = System.nanoTime();
                        inquiry.totalTime.add(finish - start);
                    }
                }
            });
            threads[i].start();
        }
        for (int i = 0; i< threadCnt; i++) {
            threads[i].join();
        }
        long end = System.nanoTime();
        double time = (double)(end - begin) / 1000000;
        System.out.println("total(ms): ");
        System.out.println(time);
        System.out.println("Request Per Second: ");
        System.out.println(threadCnt * testnum / (time / 1000));
        //Singleton.getInstance().close();
        System.out.println("inquiry(ns): ");
        System.out.println((double)inquiry.totalTime.longValue() / inquiry.methodCnt.longValue());
        System.out.println("buyTicket(ns): ");
        System.out.println((double)buy.totalTime.longValue() / buy.methodCnt.longValue());
        System.out.println("refund(ns): ");
        System.out.println((double)refund.totalTime.longValue() / refund.methodCnt.longValue());
    }
}
