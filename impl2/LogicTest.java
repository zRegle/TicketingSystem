package ticketingsystem.impl2;

import ticketingsystem.Singleton;
import ticketingsystem.Ticket;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

//思路:
//多线程并发查票, 买票, 结束后再单线程执行同样的操作, 比较两者各个区间的剩余票数是否相等
//多线程并发全部退票, 结束后检查是否恢复到原始状态
public class LogicTest {
	private static final int threadnum = 16;
	private static final int routenum = 20;
	private static final int coachnum = 15;
	private static final int seatnum = 100;
	private static final int stationnum = 10;
	private static final int testnum = 1000;
	private static final int buypc = 50;
	private static final int inqpc = 100;
	private static final Queue<Ticket> m_soldTicket = new ConcurrentLinkedQueue<>();	//多线程并发买到的票集合
	private static final Queue<Ticket> s_soldTicket = new LinkedList<>();	//单线程买到的票的集合
	private static final TicketingDS tds = new TicketingDS(routenum,coachnum,seatnum,stationnum,threadnum);	//多线程的作用对象
	private static Route[] routes;	//单线程的作用对象

	static {
		routes = new Route[routenum];
		for (int i = 0; i < routenum; i++) {
			routes[i] = new Route(stationnum, coachnum, seatnum);
		}
	}

	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid;
	}

	public static void main(String[] args) throws InterruptedException {
		//multiple threads execute
		Thread[] threads = new Thread[threadnum];
		for (int i = 0; i< threadnum; i++) {
			threads[i] = new Thread(() -> {
				Random rand = new Random();
				Ticket ticket;
				for (int j = 0; j < testnum; j++) {
					int sel = rand.nextInt(inqpc);
					if (0 <= sel && sel < buypc) { // buy ticket
						String passenger = passengerName();
						int route = rand.nextInt(routenum) + 1;
						int departure = rand.nextInt(stationnum - 1) + 1;
						int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
						if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
							m_soldTicket.offer(ticket);
							Singleton.getInstance().logMsg("TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
						} else {
							Singleton.getInstance().logMsg("TicketSoldOut" + " " + route+ " " + departure+ " " + arrival);
						}
					} else if (buypc <= sel && sel < inqpc) {
						int route = rand.nextInt(routenum) + 1;
						int departure = rand.nextInt(stationnum - 1) + 1;
						int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
						int leftTicket = tds.inquiry(route, departure, arrival);
						Singleton.getInstance().logMsg("RemainTicket" + " " + leftTicket + " " + route+ " " + departure+ " " + arrival);
					}
				}
			});
			threads[i].start();
		}
		for (int i = 0; i < threadnum; i++)
			threads[i].join();
		Singleton.getInstance().closeWriter();
		//多线程并发买票查票完毕, 单线程执行同样的买票操作
		singleThreadBuy();
		//检查两者各个区间的剩余票数是否相等
		check();
/*
 * *********************************分割线********************************************
 */
		CountDownLatch refund = new CountDownLatch(threadnum);
		//多线程并发把买到的票全部退掉
		for (int i = 0; i < threadnum; i++) {
			threads[i] = new Thread(() -> {
				while (!m_soldTicket.isEmpty()) {
					Ticket t = m_soldTicket.poll();
					tds.refundTicket(t);
				}
				refund.countDown();
			});
			threads[i].start();
		}
		for (int i = 0; i < threadnum; i++)
			threads[i].join();
		refund.await();
		//单线程把买到的票全推掉
		while (!s_soldTicket.isEmpty()) {
			Ticket t = s_soldTicket.poll();
			refund(t);
		}
		//再次检查
		check();
	}

	//检查各区间的余票是否相同
	private static void check() {
		for (int k = 1; k <= routenum; k++) {
			for (int i = 1; i < stationnum; i++) {
				for (int j = i + 1; j <= stationnum; j++) {
					int r1 = inquiry(k, i, j);
					int r2 = tds.inquiry(k, i, j);
					if (r1 != r2) {
						System.out.println(k + " " + i + " " + j + ":");
						System.out.println("single: " + r1);
						System.out.println("multiple: " + r2);
					}
				}
			}
		}
	}

	private static void singleThreadBuy() {
		//从log文件中读取操作记录
		final String file = "log.txt";
		try (FileReader fr = new FileReader(new File(file));
			 BufferedReader br = new BufferedReader(fr)) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] info = line.split(" ");
				if (info[0].equals("TicketBought")) {
					int routeID = Integer.parseInt(info[3]);
					int coach = Integer.parseInt(info[4]);
					int departure = Integer.parseInt(info[5]);
					int arrival = Integer.parseInt(info[6]);
					int seat = Integer.parseInt(info[7]);
					buyTicket(routeID, departure, arrival, coach, seat);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//单线程退票
	private static void refund(Ticket ticket) {
		int routeID = ticket.route;
		int departure = ticket.departure;
		int arrival = ticket.arrival;
		int coach = ticket.coach;
		int seat = ticket.seat;
		Route r = routes[routeID-1];
		Coach c = r.coaches[coach-1];
		Seat s = c.seats[seat-1];
		for (int i = departure; i < arrival; i++) {
			if (!s.isOccupied(i))
				Singleton.getInstance().errorMsg("seat should not be idle");
		}
		s.release(departure, arrival);
	}

	//单线程买票
	private static void buyTicket(int routID, int departure, int arrival, int coach, int seat) {
		Route r = routes[routID-1];
		Coach c = r.coaches[coach-1];
		Seat s = c.seats[seat-1];
		for (int i = departure; i < arrival; i++) {
			if (s.isOccupied(i))
				Singleton.getInstance().errorMsg("seat should not occupied");
		}
		s.take(departure, arrival);
		Ticket t = new Ticket();
		t.seat = seat;
		t.coach = coach;
		t.route = routID;
		t.departure = departure;
		t.arrival = arrival;
		s_soldTicket.offer(t);
	}

	//单线程查票
	private static int inquiry(int routID, int departure, int arrival) {
		Route r = routes[routID-1];
		int ans = 0;
		for (int i = 0; i < coachnum; i++) {
			Coach c = r.coaches[i];
			for (int j = 0; j < seatnum; j++) {
				Seat s = c.seats[j];
				boolean allIdle = true;
				for (int k = departure; k < arrival; k++) {
					if (s.isOccupied(k)) {
						allIdle = false;
						break;
					}
				}
				if (allIdle) {
					ans++;
				}
			}
		}
		return ans;
	}
}