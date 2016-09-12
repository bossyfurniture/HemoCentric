package com.runanobiolab.hemocentric.bluetooth.utils;
import java.util.UUID;

public class BleDefinedUUIDs {
	
	public static class Service {
		final static public UUID HEART_RATE               = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
		/* hm-10 default service uuid */
		final static public UUID CUSTOM_SERVICE			  = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
		/* ble112 spp-over-ble service uuid */
		//final static public UUID CUSTOM_SERVICE				  = UUID.fromString("1d5688de-866d-3aa4-ec46-a1bddb37ecf6");
	}
	
	public static class Characteristic {
		final static public UUID HEART_RATE_MEASUREMENT   = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
		final static public UUID MANUFACTURER_STRING      = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
		final static public UUID MODEL_NUMBER_STRING      = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
		final static public UUID FIRMWARE_REVISION_STRING = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
		final static public UUID APPEARANCE               = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb");
		final static public UUID BODY_SENSOR_LOCATION     = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");
		final static public UUID BATTERY_LEVEL            = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
		/* hm-10 default characteristic uuid*/
		final static public UUID CUSTOM_CHARACTERISTIC 	  = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
		/* ble112 notify-spp-over-ble characteristic uuid*/ //note, may not work
		//final static public UUID CUSTOM_CHARACTERISTIC	  = UUID.fromString("99db0f4b-b2ec-4cf8-ba6e-dd835743758c");
		/* ble112 indicate-spp-over-ble characteristic uuid*/
		//final static public UUID CUSTOM_CHARACTERISTIC	  = UUID.fromString("af20fbac-2518-4998-9af7-af42540731b3");
	}
	
	public static class Descriptor {
		final static public UUID CHAR_CLIENT_CONFIG       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	}
	
}
