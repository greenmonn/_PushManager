package kr.ac.kaist.nmsl.pushmanager.socialcontext;

import android.content.Intent;
import android.widget.Switch;

import com.google.android.gms.location.DetectedActivity;

import org.altbeacon.beacon.Beacon;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;

import be.tarsos.dsp.AudioEvent;
import kr.ac.kaist.nmsl.pushmanager.Constants;
import kr.ac.kaist.nmsl.pushmanager.activity.PhoneState;
import kr.ac.kaist.nmsl.pushmanager.audio.AudioResult;

/**
 * Created by cjpark on 2016-05-01.
 */
public class SocialContext {
    private ArrayList<ArrayList<DetectedActivity>> detectedActivitiesList;
    private ArrayList<ArrayList<Beacon>> detectedBeaconsList;
    private ArrayList<AudioResult> audioResults;

    private final Object activityLock = new Object();
    private final Object beaconLock = new Object();
    private final Object audioLock = new Object();

    public SocialContext () {
        detectedActivitiesList = new ArrayList<>();
        detectedBeaconsList = new ArrayList<>();
        audioResults = new ArrayList<>();
    }

    public HashMap<Integer, Attribute> getCurrentContext () {
        HashMap<Integer, Attribute> currentContext = new HashMap<>();

        for (Attribute attr: processActivities()) {
            currentContext.put(attr.type, attr);
        }

        for (Attribute attr: processAudioResults()) {
            currentContext.put(attr.type, attr);
        }

        for (Attribute attr: processBeacons()) {
            currentContext.put(attr.type, attr);
        }

        return currentContext;
    }

    public void addDetectedAcitivity (ArrayList<DetectedActivity> detectedActivities) {
        synchronized (activityLock) {
            detectedActivitiesList.add(detectedActivities);
        }
    }

    public void addBeacon (ArrayList<Beacon> beacons) {
        synchronized (beaconLock) {
            detectedBeaconsList.add(beacons);
        }
    }

    public void addAudioResult (AudioResult audioResult) {
        synchronized (audioLock) {
            audioResults.add(audioResult);
        }
    }

    private ArrayList<Attribute> processActivities () {
        ArrayList<Attribute> results = new ArrayList<>();

        ArrayList<ArrayList<DetectedActivity>> prev = new ArrayList<>();
        synchronized (activityLock) {
            prev.addAll(detectedActivitiesList);
            detectedActivitiesList.clear();
        }

        int totalCount = prev.size();
        int walkingCount = 0;
        int stillCount = 0;

        for (ArrayList<DetectedActivity> detectedActivities: prev) {
            if (detectedActivities.get(0).getType() == DetectedActivity.WALKING ||
                    detectedActivities.get(0).getType() == DetectedActivity.ON_FOOT) {
                walkingCount++;
            } else if (detectedActivities.get(0).getType() == DetectedActivity.STILL) {
                stillCount++;
            }
        }

        if (walkingCount >= stillCount) {
            results.add(new Attribute(Constants.CONTEXT_ATTRIBUTE_TYPES.ACTIVITY, DetectedActivity.WALKING, new Date().getTime()));
        } else {
            results.add(new Attribute(Constants.CONTEXT_ATTRIBUTE_TYPES.ACTIVITY, DetectedActivity.STILL, new Date().getTime()));
        }

        return results;
    }

    private ArrayList<Attribute> processAudioResults () {
        ArrayList<Attribute> results = new ArrayList<>();

        ArrayList<AudioResult> prev = new ArrayList<>();
        synchronized (audioLock) {
            prev.addAll(audioResults);
            audioResults.clear();
        }

        int total = prev.size();
        double spl = 0;
        int detectedPitch = 0;
        double detectedPitchThreshold = 0.8;

        for (AudioResult audioResult:prev) {
            spl+=audioResult.spl;
            if (audioResult.pitch > -1.0) {
                detectedPitch++;
            }
        }

        double pitchDetectedPortion = (double)detectedPitch/(double)total;
        double avgSPL = (double)spl/(double)total;

        results.add(new Attribute(Constants.CONTEXT_ATTRIBUTE_TYPES.PITCH, pitchDetectedPortion, new Date().getTime()));
        results.add(new Attribute(Constants.CONTEXT_ATTRIBUTE_TYPES.SPL, avgSPL, new Date().getTime()));

        return results;
    }

    private ArrayList<Attribute> processBeacons () {
        ArrayList<Attribute> results = new ArrayList<>();
        ArrayList<ArrayList<Beacon>> prev = new ArrayList<>();
        boolean isWithOthers = true;
        boolean isOtherUsingSmartphone = true;
        int isUsingCount = 0;

        synchronized (beaconLock) {
            prev.addAll(detectedBeaconsList);
            detectedBeaconsList.clear();
        }

        ArrayList<Beacon> prevBeacons = prev.get(prev.size()-1);

        //TODO: might need to set RSSI threshold.
        if (prevBeacons.size() <= 0) {
            isWithOthers = false;
        }

        for (Beacon beacon: prevBeacons) {
            if (PhoneState.getIsUsingSmartphoneFromBeacon(beacon)) {
                isUsingCount++;
            }
        }

        isOtherUsingSmartphone = isUsingCount > 0 && isUsingCount < prevBeacons.size() - 1;

        results.add(new Attribute(Constants.CONTEXT_ATTRIBUTE_TYPES.WITH_OTHERS, isWithOthers, new Date().getTime()));
        results.add(new Attribute(Constants.CONTEXT_ATTRIBUTE_TYPES.OTHER_USING_SMARTPHONE, isOtherUsingSmartphone, new Date().getTime()));

        return results;
    }

    private class Attribute {
        public int type;
        public long time;
        public double doubleValue;
        public String stringValue;
        public boolean boolValue;

        public Attribute (int type, double doubleValue, long time) {
            this.type = type;
            this.doubleValue = doubleValue;
            this.time = time;
        }

        public Attribute (int type, String stringValue, long time) {
            this.type = type;
            this.stringValue = stringValue;
            this.time = time;
        }

        public Attribute (int type, boolean boolValue, long time) {
            this.type = type;
            this.boolValue = boolValue;
            this.time = time;
        }
    }
}
