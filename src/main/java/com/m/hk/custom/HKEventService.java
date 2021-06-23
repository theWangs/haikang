package com.m.hk.custom;

import com.m.hk.lib.HCNetSDK;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: meng
 * @Date: 2021-05-31 16:07
 */
@Slf4j
@Service
public class HKEventService {
    static HCNetSDK hCNetSDK = HCNetSDK.INSTANCE;
    final HCNetSDK.FMSGCallBack_V31 callback = new HKEventCallback();
    //key: loginHandler  value: subscribeHandler
    public static ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public String startSubscribe(int loginHandler) {
        String msg = "success";
        boolean b = hCNetSDK.NET_DVR_SetDVRMessageCallBack_V31(callback, null);
        if (!b) {
            log.error("订阅失败");
        }
        HCNetSDK.NET_DVR_SETUPALARM_PARAM m_strAlarmInfo = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
        m_strAlarmInfo.dwSize = m_strAlarmInfo.size();
        m_strAlarmInfo.byLevel = 1;
        m_strAlarmInfo.byAlarmInfoType = 1;
        // m_strAlarmInfo.byDeployType = 0;// 客户端布防
        m_strAlarmInfo.byDeployType = 1;// 服务端布防
        m_strAlarmInfo.write();
        int subscribeHandler = hCNetSDK.NET_DVR_SetupAlarmChan_V41(loginHandler, m_strAlarmInfo);
        if (subscribeHandler == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.error("布防失败");
        } else {
            map.put(loginHandler, subscribeHandler);
        }
        return msg;
    }

    public void stopSubscribe(Integer subscribeHandler) {
        if (subscribeHandler == null) {
            log.error("stopSubscribe null");
            return;
        }

        boolean b = hCNetSDK.NET_DVR_CloseAlarmChan_V30(subscribeHandler);
        //防止数据紊乱（重复登陆等）导致两个map内容不一致，采用循环删除
        for (var loginHandler : map.keySet()) {
            if (subscribeHandler.intValue() == map.get(loginHandler)) {
                map.remove(loginHandler);
            }
        }
        if (!b) {
            log.error("撤防失败");
        }

    }


    /**
     * 2021-06-09
     */
    class HKEventCallback implements HCNetSDK.FMSGCallBack_V31 {

        @Override
        public boolean invoke(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
            int loginHandler = pAlarmer.lUserID;
            Integer subscribeHandler = map.get(loginHandler);
            if (subscribeHandler == null) {
                log.error("上报句柄不存在");
            }
            // lCommand是传的报警类型
            switch (lCommand) {
                case HCNetSDK.COMM_ALARM_ACS: // 门禁主机报警信息
                    try {
                        processAlarmEvent(pAlarmer, pAlarmInfo);
                    } catch (Exception e) {
                        log.error("门禁事件异常 error:{}", ExceptionUtils.getStackTrace(e));
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    }

    private void processAlarmEvent(HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo) {
        String[] sIP = new String[2];
        sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
        //报警设备ip
        String ip = sIP[0];
        String eventTime = null;
        String employeeId = null;
        String cardNo = null;
        Byte cardType = null;

        HCNetSDK.NET_DVR_ACS_ALARM_INFO strACSInfo = new HCNetSDK.NET_DVR_ACS_ALARM_INFO();
        strACSInfo.write();
        Pointer pACSInfo = strACSInfo.getPointer();
        pACSInfo.write(0, pAlarmInfo.getByteArray(0, strACSInfo.size()), 0, strACSInfo.size());
        strACSInfo.read();
        //主类型 5 事件上报
        if (strACSInfo.dwMajor != 5) {
            return;
        }
        cardNo = new String(strACSInfo.struAcsEventInfo.byCardNo).trim();
        cardType = strACSInfo.struAcsEventInfo.byCardType;
        byte timeType = strACSInfo.byTimeType;
        HCNetSDK.NET_DVR_TIME struTime = strACSInfo.struTime;
        LocalDateTime uplaodTime = LocalDateTime.of(struTime.dwYear, struTime.dwMonth, struTime.dwDay, struTime.dwHour, struTime.dwMinute, struTime.dwSecond);


        //以人为中心设备
        if (strACSInfo.byAcsEventInfoExtend == 1) {
            var test = new HCNetSDK.NET_DVR_ACS_EVENT_INFO_EXTEND();
            test.write();
            var pAcsEventInfoExtend = test.getPointer();
            pAcsEventInfoExtend.write(0, strACSInfo.pAcsEventInfoExtend.getByteArray(0, test.size()), 0, test.size());
            test.read();
            employeeId = new String(test.byEmployeeNo).trim();
        }

        if (timeType == 0) {
            eventTime = dtf.format(uplaodTime);
        } else if (timeType == 1) {
            var zonedDateTime = uplaodTime.atZone(ZoneId.from(ZoneOffset.UTC)).withZoneSameInstant(ZoneId.systemDefault());
            eventTime = dtf.format(zonedDateTime);
        } else {
            log.error("上报时间不正确");
        }
        if (strACSInfo.dwPicDataLen > 0) {
            long offset = 0;
            ByteBuffer buffers = strACSInfo.pPicData.getByteBuffer(offset, strACSInfo.dwPicDataLen);
            byte[] bytes = new byte[strACSInfo.dwPicDataLen];
            buffers.rewind();
            buffers.get(bytes);
            log.info("有图片");
        }

        // 刷卡成功
        if (strACSInfo.dwMinor == 1) {
            log.info("刷卡成功");
            //刷脸成功
        } else if (strACSInfo.dwMinor == 75) {
            log.info("刷脸成功");
            //刷脸失败
        } else if (strACSInfo.dwMinor == 76) {
            log.info("刷脸失败");
        } else {
        }

    }


}
