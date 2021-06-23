package com.m.hk.custom;

import com.m.hk.lib.HCNetSDK;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: meng
 * @Date: 2021-05-26 10:36
 */
@Slf4j
public class HKLoginService {
    static HCNetSDK hCNetSDK = HCNetSDK.INSTANCE;
    public static ConcurrentHashMap<String, Integer> loginHandleMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Integer, Integer> encodeMap = new ConcurrentHashMap<>();

    public static String login(String m_sDeviceIP, String m_sUsername,String m_sPassword){
        HCNetSDK.NET_DVR_USER_LOGIN_INFO m_strLoginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();//设备登录信息

        m_strLoginInfo.sDeviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        System.arraycopy(m_sDeviceIP.getBytes(), 0, m_strLoginInfo.sDeviceAddress, 0, m_sDeviceIP.length());

        m_strLoginInfo.sUserName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        System.arraycopy(m_sUsername.getBytes(), 0, m_strLoginInfo.sUserName, 0, m_sUsername.length());

        m_strLoginInfo.sPassword = new byte[HCNetSDK.NET_DVR_LOGIN_PASSWD_MAX_LEN];
        System.arraycopy(m_sPassword.getBytes(), 0, m_strLoginInfo.sPassword, 0, m_sPassword.length());

        m_strLoginInfo.wPort = Short.valueOf("8000");
        m_strLoginInfo.bUseAsynLogin = false; //是否异步登录：0- 否，1- 是
        m_strLoginInfo.write();

        HCNetSDK.NET_DVR_DEVICEINFO_V40 m_strDeviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();//设备信息
        int loginHandler = hCNetSDK.NET_DVR_Login_V40(m_strLoginInfo, m_strDeviceInfo);
        if (loginHandler == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            IntByReference errorInt = new IntByReference(errorCode);
            log.error("[HK] login fail errorCode:{}, errMsg:{}", errorCode, hCNetSDK.NET_DVR_GetErrorMsg(errorInt));
            return hCNetSDK.NET_DVR_GetErrorMsg(errorInt);
        } else {
            loginHandleMap.put(m_sDeviceIP,loginHandler);
            int iCharEncodeType = m_strDeviceInfo.byCharEncodeType;
            encodeMap.put(loginHandler,iCharEncodeType);
            log.info("[HK] login success iCharEncodeType:{}", iCharEncodeType);
            log.info("[HK] login map:{}", loginHandleMap);
            return "success";
        }
    }

    public static String loginOut(Integer loginHandler){
        if(loginHandler == null){
            log.error("[HK] logout null");
            return "success";
        }

        boolean b = hCNetSDK.NET_DVR_Logout(loginHandler);
        for (String ip : loginHandleMap.keySet()) {
            if(loginHandler.intValue() == loginHandleMap.get(ip)){
                loginHandleMap.remove(ip);
            }
        }
        if(!b){
            log.error("[HK] logout fail");
        }
        log.info("[HK] logout map:{}", loginHandleMap);
        return "success";
    }

}
