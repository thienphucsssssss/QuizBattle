package vn.edu.hutech.nqb.client;

/** Host timestamps distinguish cached BSS entries from newly received beacon/probe frames. */
public record WifiObservation(String ssid,String bssid,int rssiDbm,int frequencyKhz,
                              long hostTimestamp100ns,long ageMillis,boolean fresh,boolean associated) {
    public int channel(){return WifiSignalProvider.channelFromFrequencyKhz(frequencyKhz);}
}
