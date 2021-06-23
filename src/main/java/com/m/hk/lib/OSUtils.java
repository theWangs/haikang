package com.m.hk.lib;

import com.sun.jna.Platform;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class OSUtils {

	
	// 获取操作平台信息
	public static String getOsPrefix() {
		String arch = System.getProperty("os.arch").toLowerCase();
		final String name = System.getProperty("os.name");
		String osPrefix;
		switch(Platform.getOSType()) {
			case Platform.WINDOWS: {
				if ("i386".equals(arch))
	                arch = "x86";
	            osPrefix = "win32-" + arch;
			}
            break;
			case Platform.LINUX: {
				if ("x86".equals(arch)) {
	                arch = "i386";
	            }
	            else if ("x86_64".equals(arch)) {
	                arch = "amd64";
	            }
	            osPrefix = "linux-" + arch;
			}			       
	        break;
			default: {
	            osPrefix = name.toLowerCase();
	            if ("x86".equals(arch)) {
	                arch = "i386";
	            }
	            if ("x86_64".equals(arch)) {
	                arch = "amd64";
	            }
	            int space = osPrefix.indexOf(" ");
	            if (space != -1) {
	                osPrefix = osPrefix.substring(0, space);
	            }
	            osPrefix += "-" + arch;
			}
	        break;
	       
		}

		return osPrefix;
	}	
	
    public static String getOsName() {
    	String osName = "";
		String osPrefix = getOsPrefix();
		if(osPrefix.toLowerCase().startsWith("win32-x86")
				||osPrefix.toLowerCase().startsWith("win32-amd64") ) {
			osName = "win";
		} else if(osPrefix.toLowerCase().startsWith("linux-i386")
				|| osPrefix.toLowerCase().startsWith("linux-amd64")) {
			osName = "linux";
		}
		
		return osName;
    }

	/**
	 * 获取库文件
	 * 区分win、linux
	 * @return
	 */
	public static String getLoadLibrary() {
		if (isChecking()) {
			return null;
		}
		String loadLibrary = "";
		String library = "";
		String osPrefix = getOsPrefix();
		if(osPrefix.toLowerCase().startsWith("win32-x86")) {
			loadLibrary = System.getProperty("user.dir")  + File.separator+ "hklib" + File.separator;
			library = "HCNetSDK.dll";
		} else if(osPrefix.toLowerCase().startsWith("win32-amd64") ) {
			loadLibrary = System.getProperty("user.dir")  + File.separator+ "hklib" + File.separator;
			library = "HCNetSDK.dll";
		} else if(osPrefix.toLowerCase().startsWith("linux-i386")) {
			loadLibrary = "/lib/";
			library = "libhcnetsdk.so";
		}else if(osPrefix.toLowerCase().startsWith("linux-amd64")) {
			loadLibrary = "/lib/";
			library = "libhcnetsdk.so";
		}
		
		log.info("================= Load library Path :{} ==================", loadLibrary + library);
		return loadLibrary + library;
	}
	
	private static boolean checking = false;
	public static void setChecking() {
		checking = true;
	}
	public static void clearChecking() {
		checking = false;
	}
	public static boolean isChecking() {
		return checking;
	}
}
