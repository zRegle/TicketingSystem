package ticketingsystem.impl2;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Route {
    int stationNum;
    int coachNum;
    Coach[] coaches;
    AtomicInteger[][] left;
    public Route (int stationNum, int coachNum, int seatNum) {
        this.stationNum = stationNum;
        this.coachNum = coachNum;
        //初始化
        left = new AtomicInteger[stationNum + 1][stationNum + 1];
        for (int i = 1; i < stationNum; i++) {
            left[i] = new AtomicInteger[stationNum + 1];
            for (int j = i + 1; j <= stationNum; j++)
                left[i][j] = new AtomicInteger(0);
        }
        left[1][stationNum].set(coachNum * seatNum);
        //初始化车厢
        coaches = new Coach[coachNum];
        for (int i = 0; i < coachNum; i++) {
            coaches[i] = new Coach(stationNum, seatNum);
        }
    }
}

class Coach {
    Seat[] seats;
    int seatNum;
    AtomicInteger[][] left;
    public Coach(int stationNum, int seatNum) {
        this.seatNum = seatNum;
        //初始化
        left = new AtomicInteger[stationNum + 1][stationNum + 1];
        for (int i = 1; i < stationNum; i++) {
            left[i] = new AtomicInteger[stationNum + 1];
            for (int j = i + 1; j <= stationNum; j++)
                left[i][j] = new AtomicInteger(0);
        }
        left[1][stationNum].set(seatNum);
        //初始化座位
        seats = new Seat[seatNum];
        for (int i = 0; i < seatNum; i++) {
            seats[i] = new Seat();
        }
    }
}

class Seat {
    AtomicBoolean occupying;
    //比如5个车站, 初始化为1111, 各个区间都是空闲的
    //从低位开始, 第一个1表示1->2区间, 第二个1表示区间2->3, 以此类推
    //某个bit为0表示该区间被占了
    private int bitmap;
    public Seat() {
        bitmap = Integer.MAX_VALUE;
        occupying = new AtomicBoolean(false);
    }

    boolean isOccupied(int departure) {
        int res = bitmap & (1 << (departure - 1));
        return res == 0;
    }

    void take(int departure, int arrival) {
        for (int i = departure; i < arrival; i++) {
            bitmap &= ~(1 << (i - 1));
        }
    }

    void release(int departure, int arrival) {
        for (int i = departure; i < arrival; i++) {
            bitmap |= (1 << (i - 1));
        }
    }
}