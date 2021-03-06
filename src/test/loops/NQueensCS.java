/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

//import jsr166y.*;
import java.util.*;
import java.util.concurrent.*;

public final class NQueensCS extends RecursiveAction {

    static long lastStealCount;
    static int boardSize;

    static final int[] expectedSolutions = new int[] {
        0, 1, 0, 0, 2, 10, 4, 40, 92, 352, 724, 2680, 14200,
        73712, 365596, 2279184, 14772512, 95815104, 666090624
    }; // see http://www.durangobill.com/N_Queens.html

    static final int FIRST_SIZE = 8; // smaller ones too short to measure well
    static final int LAST_SIZE = 15; // bigger ones too long to wait for

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        int procs = 0;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
        }
        catch (Exception e) {
            System.out.println("Usage: java NQueensCS <threads> ");
            return;
        }
        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
                new ForkJoinPool(procs);
            lastStealCount = g.getStealCount();
            for (int i = FIRST_SIZE; i <= LAST_SIZE; i++)
                test(g, i);
            System.out.println(g);
            g.shutdown();
        }
    }

    static void test(ForkJoinPool g, int i) throws Exception {
        boardSize = i;
        int ps = g.getParallelism();
        long start = System.nanoTime();
        NQueensCS task = new NQueensCS(new int[0]);
        g.invoke(task);
        int solutions = task.solutions;
        long time = System.nanoTime() - start;
        double secs = ((double)time) / NPS;
        if (solutions != expectedSolutions[i])
            throw new Error();
        System.out.printf("NQueensCS %3d", i);
        System.out.printf(" Time: %7.3f", secs);
        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        System.out.println();
    }

    // Boards are represented as arrays where each cell
    // holds the column number of the queen in that row

    final int[] sofar;
    NQueensCS nextSubtask; // to link subtasks
    int solutions;
    NQueensCS(int[] a) {
        this.sofar = a;
    }

    public final void compute() {
        NQueensCS subtasks;
        int bs = boardSize;
        if (sofar.length >= bs)
            solutions = 1;
        else if ((subtasks = explore(sofar, bs)) != null)
            solutions = processSubtasks(subtasks);
    }

    private static NQueensCS explore(int[] array, int bs) {
        int row = array.length;
        NQueensCS s = null; // subtask list
        outer:
        for (int q = 0; q < bs; ++q) {
            for (int i = 0; i < row; i++) {
                int p = array[i];
                if (q == p || q == p - (row - i) || q == p + (row - i))
                    continue outer; // attacked
            }
            NQueensCS first = s; // lag forks to ensure 1 kept
            if (first != null)
                first.fork();
            int[] next = Arrays.copyOf(array, row+1);
            next[row] = q;
            NQueensCS subtask = new NQueensCS(next);
            subtask.nextSubtask = first;
            s = subtask;
        }
        return s;
    }

    private static int processSubtasks(NQueensCS s) {
        // Always run first the task held instead of forked
        s.compute();
        int ns = s.solutions;
        s = s.nextSubtask;
        // Then the unstolen ones
        while (s != null && s.tryUnfork()) {
            s.compute();
            ns += s.solutions;
            s = s.nextSubtask;
        }
        // Then wait for the stolen ones
        while (s != null) {
            s.join();
            ns += s.solutions;
            s = s.nextSubtask;
        }
        return ns;
    }
}
