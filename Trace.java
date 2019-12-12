package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

class ThreadId {
    // Atomic integer containing the next thread ID to be assigned
    private static final AtomicInteger nextId = new AtomicInteger(0);

    // Thread local variable containing each thread's ID
    private static final ThreadLocal<Integer> threadId =
        new ThreadLocal<Integer>() {
            @Override protected Integer initialValue() {
                return nextId.getAndIncrement();
        }
    };

    // Returns the current thread's unique ID, assigning it if necessary
    public static int get() {
        return threadId.get();
    }
}

public class Trace {
	private final static int threadnum = 16; // concurrent thread number
	private final static int routenum = 5; // route is designed from 1 to 3
	private final static int coachnum = 8; // coach is arranged from 1 to 5
	private final static int seatnum = 100; // seat is allocated from 1 to 20
	private final static int stationnum = 8; // station is designed from 1 to 5
	private final static int testnum = 4000;
	private final static int retpc = 10; // return ticket operation is 10% percent
	private final static int buypc = 40; // buy ticket operation is 30% percent
	private final static int inqpc = 100; //inquiry ticket operation is 60% percent
	
	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid; 
	}

	public static void main(String[] args) throws InterruptedException {
        
		 
		Thread[] threads = new Thread[threadnum];
		long begin = System.currentTimeMillis();
		final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
	    for (int i = 0; i< threadnum; i++) {
	    	threads[i] = new Thread(new Runnable() {
                public void run() {
            		Random rand = new Random();
                	Ticket ticket = new Ticket();
            		ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();
            		
            		//System.out.println(ThreadId.get());
            		for (int i = 0; i < testnum; i++) {
            			int sel = rand.nextInt(inqpc);
            			if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
            				int select = rand.nextInt(soldTicket.size());
           					if ((ticket = soldTicket.remove(select)) != null) {
           						if (tds.refundTicket(ticket)) {
           							//System.out.println("TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach  + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
									//System.out.flush();
									Singleton.INSTANCE.logMsg("TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach  + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
           						} else {
           							System.out.println("ErrOfRefund");
           							System.out.flush();
           						}
           					} else {
           						System.out.println("ErrOfRefund");
           						System.out.flush();
           					}
            			} else if (retpc <= sel && sel < buypc) { // buy ticket
            				String passenger = passengerName();
            				int route = rand.nextInt(routenum) + 1;
            				int departure = rand.nextInt(stationnum - 1) + 1;
            				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
            				if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
            					soldTicket.add(ticket);
            					//System.out.println("TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
        						//System.out.flush();
								Singleton.INSTANCE.logMsg("TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
            				} else {
            					//System.out.println("TicketSoldOut" + " " + route+ " " + departure+ " " + arrival);
        						//System.out.flush();
								Singleton.INSTANCE.logMsg("TicketSoldOut" + " " + route+ " " + departure+ " " + arrival);
            				}
            			} else if (buypc <= sel && sel < inqpc) { // inquiry ticket
            				int route = rand.nextInt(routenum) + 1;
            				int departure = rand.nextInt(stationnum - 1) + 1;
            				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
            				int leftTicket = tds.inquiry(route, departure, arrival);
            				//System.out.println("RemainTicket" + " " + leftTicket + " " + route+ " " + departure+ " " + arrival);
    						//System.out.flush();
							Singleton.INSTANCE.logMsg("RemainTicket" + " " + leftTicket + " " + route+ " " + departure+ " " + arrival);
            			}
            		}
                }
            });
	    	threads[i].start();
 	    }			
	
	    for (int i = 0; i< threadnum; i++) {
	    	threads[i].join();
	    }
		long end = System.currentTimeMillis();
		System.out.println(end - begin);
	    Singleton.INSTANCE.closeWriter();
	}
}
