package edu.swiftattend;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttendanceSecurityTest {
    @Test void hmacBindsSessionTimestampAndNonce(){byte[] key=new byte[32];new Random(4).nextBytes(key);String a=AttendanceController.sign(key,"session:1000:NONCE");assertEquals(64,a.length());assertEquals(a,AttendanceController.sign(key,"session:1000:NONCE"));assertNotEquals(a,AttendanceController.sign(key,"other:1000:NONCE"));assertNotEquals(a,AttendanceController.sign(key,"session:1001:NONCE"));assertNotEquals(a,AttendanceController.sign(key,"session:1000:OTHER"));}
    @Test void timestampWindowAllowsCurrentAndRecentCodesButRejectsExpiredAndFuture(){long now=10_000;assertEquals(AttendanceController.TimestampResult.VALID,AttendanceController.timestampStatus(now,now,2000,1000));assertEquals(AttendanceController.TimestampResult.VALID,AttendanceController.timestampStatus(now-2000,now,2000,1000));assertEquals(AttendanceController.TimestampResult.EXPIRED,AttendanceController.timestampStatus(now-2001,now,2000,1000));assertEquals(AttendanceController.TimestampResult.VALID,AttendanceController.timestampStatus(now+1000,now,2000,1000));assertEquals(AttendanceController.TimestampResult.FUTURE,AttendanceController.timestampStatus(now+1001,now,2000,1000));}
    @Test void faceVectorDistanceEnforces128DimensionsAndThreshold(){List<Double> face=new ArrayList<>(Collections.nCopies(128,0.05));List<Double> same=new ArrayList<>(face);List<Double> other=new ArrayList<>(face);other.set(0,0.8);assertEquals(0,AttendanceController.distance(face,same));assertTrue(AttendanceController.distance(face,other)>0.45);assertTrue(Double.isInfinite(AttendanceController.distance(face,face.subList(0,127))));}
    @Test void haversineAcceptsNearbyAndRejectsRemoteLocations(){double originLat=37.4219999,originLon=-122.0840575;assertTrue(AttendanceController.distanceMeters(originLat,originLon,originLat,originLon)<1);assertTrue(AttendanceController.distanceMeters(originLat,originLon,37.4221,-122.0840)<30);assertTrue(AttendanceController.distanceMeters(originLat,originLon,37.43,-122.08)>500);}
}
