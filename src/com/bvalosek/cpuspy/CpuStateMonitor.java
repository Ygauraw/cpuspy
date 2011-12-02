//-----------------------------------------------------------------------------
//
// (C) Brandon Valosek, 2011 <bvalosek@gmail.com>
//
//-----------------------------------------------------------------------------

package com.bvalosek.cpuspy;

// imports
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.SystemClock;
import android.util.Log;

/**
 * CpuStateMonitor is a class responsible for querying the system and getting
 * the time-in-state information, as well as allowing the user to set/reset
 * offsets to "restart" the state timers
 */
public class CpuStateMonitor {

    public static final String TIME_IN_STATE_PATH =
        "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state";
    public static final String VERSION_PATH = "/proc/version";

    private static final String TAG = "CpuStateMonitor";

    private List<CpuState> _states = new ArrayList<CpuState>();
    private Map<Integer, Integer> _offsets = new HashMap<Integer, Integer>();

    /** exception class */
    public class CpuStateMonitorException extends Exception {
        public CpuStateMonitorException(String s) {
            super(s);
        }
    }

    /**
     * simple struct for states/time
     */
    public class CpuState implements Comparable<CpuState> {
        /** init with freq and duration */
        public CpuState(int a, int b) { freq = a; duration = b; }

        public int freq = 0;
        public int duration = 0;

        /** for sorting, compare the freqs */
        public int compareTo(CpuState state) {
            Integer a = new Integer(freq);
            Integer b = new Integer(state.freq);
            return a.compareTo(b);
        }
    }

    /** @return List of CpuState of the CPU */
    public List<CpuState> getTimeInStates() {
        return _states;
    }

    /** @return List of CpuState with the offsets applied */
    public List<CpuState> getTimeInStatesWithOffsets() {
        List<CpuState> states = new ArrayList<CpuState>();

        /* check for an existing offset, and if it's not too big, subtract it
         * from the duration, otherwise just add it to the return List */
        for (CpuState state : _states) {
            int duration = state.duration;
            if (_offsets.containsKey(state.freq)) {
                int offset = _offsets.get(state.freq);
                if (offset < duration) {
                    duration -= offset;
                }
            }

            states.add(new CpuState(state.freq, duration));
        }

        return states;
    }

    /** @return Sum of all state durations, including deep sleep */
    public int getTotalStateTime() {
        int sum = 0;

        for (CpuState state : _states) {
            sum += state.duration;
        }

        return sum;
    }

    /**
     * @return Sum of all state durations including deep sleep, accounting
     * for offsets
     */
    public int getTotalStateTimeIncludingOffsets() {
        int sum = 0;

        for (Map.Entry<Integer, Integer> entry : _offsets.entrySet()) {
            sum += entry.getValue();
        }

        return getTotalStateTime() - sum;
    }

    /**
     * Sets the offset map (freq->duration offset), used to allowing the
     * user to "reset" the state timers"
     */
    public void setStateOffests(Map<Integer, Integer> offsets) {
        _offsets = offsets;
    }

    /**
     * Updates the current time in states and then sets the offset map to the
     * current duration, effectively "zeroing out" the timers
     */
    public void setStateOffsets() throws CpuStateMonitorException {
        _offsets.clear();
        updateTimeInStates();
        for (CpuState state : _states) {
            _offsets.put(state.freq, state.duration);
        }
    }

    /** removes state offsets */
    public void removeOffsets() {
        _offsets.clear();
    }

    /**
     * @return a list of all the CPU frequency states, which contains
     * both a frequency and a duration (time spent in that state
     */
    public List<CpuState> updateTimeInStates()
        throws CpuStateMonitorException {
        /* attempt to create a buffered reader to the time in state
         * file and read in the states to the class */
        try {
            InputStream is = new FileInputStream(TIME_IN_STATE_PATH);
            InputStreamReader ir = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(ir);
            readInStates(br);
            is.close();
        } catch (IOException e) {
            throw new CpuStateMonitorException(
                    "Problem opening time-in-states file");
        }

        /* deep sleep time determined by difference between elapsed
         * (total) boot time and the system uptime (awake) */
        int sleepTime = (int)(SystemClock.elapsedRealtime()
                - SystemClock.uptimeMillis()) / 10;
        _states.add(new CpuState(0, sleepTime));

        Collections.sort(_states, Collections.reverseOrder());

        return _states;
    }

    /** read from a provided BufferedReader the state lines into the
     * States member field
     */
    private void readInStates(BufferedReader br)
        throws CpuStateMonitorException {
        try {
            _states.clear();
            String line;
            while ((line = br.readLine()) != null) {
                // split open line and convert to Integers
                String[] nums = line.split(" ");
                _states.add(new CpuState(
                        Integer.parseInt(nums[0]),
                        Integer.parseInt(nums[1])));
            }
        } catch (IOException e) {
            throw new CpuStateMonitorException(
                    "Problem processing time-in-states file");
        }
    }
}
