package ticketingsystem.impl1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class Route {
    int departure;
    int arrival;
    int routeID;
    int coachNum;
    int seatNum;
    int total_seat;
    int stationNum;
    State[][] states; //states[1][2]: 1->2, states[2][5]: 2->5, 以此类推

    Route(int departure, int arrival, int routID, int coachNum, int seatNum) {
        this.departure = departure;
        this.arrival = arrival;
        this.routeID = routID;
        this.coachNum = coachNum;
        this.seatNum = seatNum;
        total_seat = coachNum * seatNum;
        this.stationNum = arrival;
        states = new State[arrival+1][arrival+1];
        for (int i = 1; i < arrival; i++) {
            for (int j = i + 1; j <= arrival; j++) {
                states[i][j] = new State(total_seat);
            }
        }
    }
}

class State {
    //availableSeats: 空闲的座位集合
    //key -> 座位标号, value -> 写死为true
    ConcurrentHashMap<Integer, Boolean> availableSeats;
    AtomicInteger left; //剩余票数

    State(int totalSeat) {
        left = new AtomicInteger(totalSeat);
        availableSeats = new ConcurrentHashMap<>(totalSeat);
        for (int i = 1; i <= totalSeat; i++)
            availableSeats.put(i, true);
    }
}