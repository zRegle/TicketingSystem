package ticketingsystem;

import java.util.Random;

public class Test {
	private final static int threadnum = 4; // concurrent thread number
	private final static int routenum = 3; // route is designed from 1 to 3
	private final static int coachnum = 3; // coach is arranged from 1 to 5
	private final static int seatnum = 3; // seat is allocated from 1 to 20
	private final static int stationnum = 3; // station is designed from 1 to 5
	private final static int testnum = 4000;
	public static void main(String[] args) throws InterruptedException {
		final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
	}

	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid;
	}
}
