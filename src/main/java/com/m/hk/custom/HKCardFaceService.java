package com.m.hk.custom;

import com.m.hk.lib.HCNetSDK;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * 明眸设备，以卡为中心
 *
 * @Author: meng
 * @Date: 2021-05-26 10:51
 */
@Slf4j
@Component
public class HKCardFaceService {

    static HCNetSDK hCNetSDK = HCNetSDK.INSTANCE;


    ////字符编码类型：0- 无字符编码信息(老设备)，1- GB2312(简体中文)，2- GBK，3- BIG5(繁体中文)，
    // 4- Shift_JIS(日文)，5- EUC-KR(韩文)，6- UTF-8，7- ISO8859-1，8- ISO8859-2，9- ISO8859-3，…，依次类推，21- ISO8859-15(西欧)
    //Demo注释
    //根据iCharEncodeType判断，如果iCharEncodeType返回6，则是UTF-8编码。
    //如果是0或者1或者2，则是GBK编码
    public String getCharsetName(int loginHandle) {
        Integer code = HKLoginService.encodeMap.get(loginHandle);
        switch (code) {
            case 0:
            case 1:
            case 2:
                return "GBK";
            case 6:
                return "UTF-8";
            case 7:
                return "ISO8859-1";
        }
        return null;
    }

    public String addCard(int loginHandler, String strCardNo, String inputName) {
        String msg = "success";
        HCNetSDK.NET_DVR_CARD_COND struCardCond = new HCNetSDK.NET_DVR_CARD_COND();
        struCardCond.read();
        struCardCond.dwSize = struCardCond.size();
        struCardCond.dwCardNum = 1;  //下发一张
        struCardCond.write();
        Pointer ptrStruCond = struCardCond.getPointer();

        int m_lSetCardCfgHandle = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, HCNetSDK.NET_DVR_SET_CARD, ptrStruCond, struCardCond.size(), null, null);
        if (m_lSetCardCfgHandle == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.error("fail ，errorCode:{}, msg :{}", errorCode, msg);
            return msg;
        }

        HCNetSDK.NET_DVR_CARD_RECORD struCardRecord = new HCNetSDK.NET_DVR_CARD_RECORD();
        struCardRecord.read();
        struCardRecord.dwSize = struCardRecord.size();

        for (int i = 0; i < HCNetSDK.ACS_CARD_NO_LEN; i++) {
            struCardRecord.byCardNo[i] = 0;
        }
        byte[] cardNoBytes = strCardNo.getBytes();
        for (int i = 0; i < strCardNo.length(); i++) {
            struCardRecord.byCardNo[i] = cardNoBytes[i];
        }
        struCardRecord.byCardType = 1; //普通卡
        struCardRecord.byLeaderCard = 0; //是否为首卡，0-否，1-是   不知道啥区别
        struCardRecord.byUserType = 0;
        struCardRecord.byDoorRight[0] = 1; //门1有权限
        struCardRecord.wCardRightPlan[0] = (short) 1;//关联门计划模板，使用默认模板 1 24小时有权限

        struCardRecord.struValid.byEnable = 1;    //卡有效期使能，下面是卡有效期从2000-1-1 11:11:11到2030-1-1 11:11:11
        struCardRecord.struValid.struBeginTime.wYear = 2020;
        struCardRecord.struValid.struBeginTime.byMonth = 1;
        struCardRecord.struValid.struBeginTime.byDay = 1;
        struCardRecord.struValid.struBeginTime.byHour = 11;
        struCardRecord.struValid.struBeginTime.byMinute = 11;
        struCardRecord.struValid.struBeginTime.bySecond = 11;
        struCardRecord.struValid.struEndTime.wYear = 2035;
        struCardRecord.struValid.struEndTime.byMonth = 1;
        struCardRecord.struValid.struEndTime.byDay = 1;
        struCardRecord.struValid.struEndTime.byHour = 11;
        struCardRecord.struValid.struEndTime.byMinute = 11;
        struCardRecord.struValid.struEndTime.bySecond = 11;

        // struCardRecord.dwEmployeeNo = 66611; //工号自动生成
        String charsetName = getCharsetName(loginHandler);
        byte[] nameByte = null;
        if (charsetName != null) {
            try {
                nameByte = inputName.getBytes(charsetName);
            } catch (UnsupportedEncodingException e) {
                log.error("name charset convert error charsetName:{}", charsetName);
            }
        } else {
            nameByte = inputName.getBytes();
        }
        System.arraycopy(nameByte, 0, struCardRecord.byName, 0, nameByte.length);

        struCardRecord.write();

        HCNetSDK.NET_DVR_CARD_STATUS struCardStatus = new HCNetSDK.NET_DVR_CARD_STATUS();
        struCardStatus.read();
        struCardStatus.dwSize = struCardStatus.size();
        struCardStatus.write();

        IntByReference pInt = new IntByReference(0);

        while (true) {
            int dwState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(m_lSetCardCfgHandle, struCardRecord.getPointer(), struCardRecord.size(), struCardStatus.getPointer(), struCardStatus.size(), pInt);
            struCardStatus.read();
            if (dwState == -1) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("fail ，errorCode:{}, msg :{}", errorCode, msg);
            } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_NEEDWAIT) {
                log.error("wait");
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                log.error("下发卡失败, cardNo:{}, errorCode:{}",new String(struCardStatus.byCardNo).trim(), struCardStatus.dwErrorCode );
                break;
            } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                log.error("下发卡异常, cardNo:{}, errorCode:{}",new String(struCardStatus.byCardNo).trim(), struCardStatus.dwErrorCode);
                break;
            } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {
                if (struCardStatus.dwErrorCode != 0) {
                    log.error("下发卡成功,但是有错误, cardNo:{}, errorCode:{}",new String(struCardStatus.byCardNo).trim(), struCardStatus.dwErrorCode);
                } else {
                    log.info("下发卡成功");
                }
                continue;
            } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                log.info("下发卡完成");
                break;
            }
        }
        msg = stopRemoteConfig(msg, m_lSetCardCfgHandle, "add Card");
        return msg;
    }

    public String addFace(int loginHandler, String cardNo, byte[] faceByte) {
        String msg = "success";
        HCNetSDK.NET_DVR_FACE_COND struFaceCond = new HCNetSDK.NET_DVR_FACE_COND();
        struFaceCond.read();
        struFaceCond.dwSize = struFaceCond.size();
        struFaceCond.byCardNo = cardNo.getBytes();
        struFaceCond.dwFaceNum = 1;  //下发一张
        struFaceCond.dwEnableReaderNo = 1;//人脸读卡器编号
        struFaceCond.write();
        Pointer ptrStruFaceCond = struFaceCond.getPointer();

        int m_lSetFaceCfgHandle = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, HCNetSDK.NET_DVR_SET_FACE, ptrStruFaceCond, struFaceCond.size(), null, null);
        if (m_lSetFaceCfgHandle == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("fail ，errorCode:{}, msg :{}", errorCode, msg);
            return msg;
        }

        HCNetSDK.NET_DVR_FACE_RECORD struFaceRecord = new HCNetSDK.NET_DVR_FACE_RECORD();
        struFaceRecord.read();
        struFaceRecord.dwSize = struFaceRecord.size();

        for (int i = 0; i < HCNetSDK.ACS_CARD_NO_LEN; i++) {
            struFaceRecord.byCardNo[i] = 0;
        }
        for (int i = 0; i < cardNo.length(); i++) {
            struFaceRecord.byCardNo[i] = cardNo.getBytes()[i];
        }

        HCNetSDK.BYTE_ARRAY ptrpicByte = new HCNetSDK.BYTE_ARRAY(faceByte.length);
        ptrpicByte.byValue = faceByte;
        ptrpicByte.write();
        struFaceRecord.dwFaceLen = faceByte.length;
        struFaceRecord.pFaceBuffer = ptrpicByte.getPointer();
        struFaceRecord.write();
        HCNetSDK.NET_DVR_FACE_STATUS struFaceStatus = new HCNetSDK.NET_DVR_FACE_STATUS();
        struFaceStatus.read();
        struFaceStatus.dwSize = struFaceStatus.size();
        struFaceStatus.write();
        IntByReference pInt = new IntByReference(0);
        while(true){
            int dwFaceState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(m_lSetFaceCfgHandle, struFaceRecord.getPointer(), struFaceRecord.size(),struFaceStatus.getPointer(), struFaceStatus.size(),  pInt);
            struFaceStatus.read();
            if(dwFaceState == -1){
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("fail ，errorCode:{}, msg :{}", errorCode, msg);
                break;
            } else if(dwFaceState == HCNetSDK.NET_SDK_CONFIG_STATUS_NEEDWAIT) {
                log.error("wait");
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            } else if(dwFaceState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("下发人脸失败, 卡号, cardNo:{}, errorCode:{}, msg :{}", cardNo, errorCode, msg);
                break;
            } else if(dwFaceState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("下发人脸异常, 卡号, cardNo:{}, errorCode:{}, msg :{}", cardNo, errorCode, msg);
                break;
            }else if(dwFaceState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {
                if (struFaceStatus.byRecvStatus != 1){
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.error("下发人脸联通成功，但是操作失败, 卡号, cardNo:{}, errorCode:{}, msg :{}", cardNo, errorCode, msg);
                    break;
                } else{
                    log.info("下发人脸成功, 卡号, cardNo:{}", cardNo);
                }
                continue;
            }
            else if(dwFaceState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                log.info("下发人脸完成, 卡号, cardNo:{}", cardNo);
                break;
            }
        }
        msg = stopRemoteConfig(msg, m_lSetFaceCfgHandle, "add Face");
        return msg;
    }

    /**
     * 删除卡号，会关联删除用户的人脸
     * @param loginHandler
     * @param strCardNo
     * @return
     */
    public String deleteCard(int loginHandler, String strCardNo) {
        String msg = "success";
        HCNetSDK.NET_DVR_CARD_COND struCardCond = new HCNetSDK.NET_DVR_CARD_COND();
        struCardCond.read();
        struCardCond.dwSize = struCardCond.size();
        struCardCond.dwCardNum = 1;  //下发一张
        struCardCond.write();
        Pointer ptrStruCond = struCardCond.getPointer();

        int m_lSetCardCfgHandle = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, HCNetSDK.NET_DVR_DEL_CARD, ptrStruCond, struCardCond.size(), null, null);
        if (m_lSetCardCfgHandle == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("NET_DVR_StartRemoteConfig fail ，errorCode:{}, msg :{}", errorCode, msg);
            return msg;
        }

        HCNetSDK.NET_DVR_CARD_SEND_DATA struCardData = new HCNetSDK.NET_DVR_CARD_SEND_DATA();
        struCardData.read();
        struCardData.dwSize = struCardData.size();

        for (int i = 0; i < HCNetSDK.ACS_CARD_NO_LEN; i++) {
            struCardData.byCardNo[i] = 0;
        }
        for (int i = 0; i < strCardNo.length(); i++) {
            struCardData.byCardNo[i] = strCardNo.getBytes()[i];
        }
        struCardData.write();

        HCNetSDK.NET_DVR_CARD_STATUS struCardStatus = new HCNetSDK.NET_DVR_CARD_STATUS();
        struCardStatus.read();
        struCardStatus.dwSize = struCardStatus.size();
        struCardStatus.write();

        IntByReference pInt = new IntByReference(0);

        while(true){
           int dwState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(m_lSetCardCfgHandle, struCardData.getPointer(), struCardData.size(),struCardStatus.getPointer(), struCardStatus.size(),  pInt);
            struCardStatus.read();
            if(dwState == -1){
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("接口调用失败, cardNo, errorCode:{}, msg :{}", strCardNo, errorCode, msg);
                break;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_NEEDWAIT) {
                log.error("wait ");
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("删除卡失败, cardNo:{}, errorCode:{}, msg :{}", strCardNo, errorCode, msg);
                break;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("删除卡异常 , cardNo:{}, errorCode:{}, msg :{}", strCardNo, errorCode, msg);
                break;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {
                if (struCardStatus.dwErrorCode != 0){
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.error("删除卡联通成功,但是有错误 , cardNo:{}, errorCode:{}, msg :{}", strCardNo, errorCode, msg);
                } else{
                    log.error("删除卡联通成功 cardNo:{}", strCardNo);
                }
                continue;
            }
            else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                log.error("删除卡联通完成 cardNo:{}", strCardNo);
                break;
            }
        }

        msg = stopRemoteConfig(msg, m_lSetCardCfgHandle, "delete card");
        return msg;
    }

    public String getCard(int loginHandler, String strCardNo) {
        String msg = "success";
        HCNetSDK. NET_DVR_CARD_COND struCardCond = new HCNetSDK.NET_DVR_CARD_COND();
        struCardCond.read();
        struCardCond.dwSize = struCardCond.size();
        struCardCond.dwCardNum = 1; //查询一个卡参数
        struCardCond.write();
        Pointer ptrStruCond = struCardCond.getPointer();

        int m_lSetCardCfgHandle = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, HCNetSDK.NET_DVR_GET_CARD, ptrStruCond, struCardCond.size(),null ,null);
        if (m_lSetCardCfgHandle == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("NET_DVR_StartRemoteConfig fail ，errorCode:{}, msg :{}", errorCode, msg);
            return msg;
        }
        //查找指定卡号的参数，需要下发查找的卡号条件
        HCNetSDK.NET_DVR_CARD_SEND_DATA struCardNo = new HCNetSDK.NET_DVR_CARD_SEND_DATA();
        struCardNo.read();
        struCardNo.dwSize = struCardNo.size();

        for (int i = 0; i < HCNetSDK.ACS_CARD_NO_LEN; i++) {
            struCardNo.byCardNo[i] = 0;
        }
        for (int i = 0; i <  strCardNo.length(); i++) {
            struCardNo.byCardNo[i] = strCardNo.getBytes()[i];
        }
        struCardNo.write();
        HCNetSDK.NET_DVR_CARD_RECORD struCardRecord = new HCNetSDK.NET_DVR_CARD_RECORD();
        struCardRecord.read();

        IntByReference pInt = new IntByReference(0);

        while(true){
            int dwState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(m_lSetCardCfgHandle, struCardNo.getPointer(), struCardNo.size(), struCardRecord.getPointer(), struCardRecord.size(), pInt);
            struCardRecord.read();

            if(dwState == -1){
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("接口调用失败, cardNo, errorCode:{}, msg :{}", strCardNo, errorCode, msg);
                break;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_NEEDWAIT) {
                log.error("wait ");
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("失败, cardNo:{}, errorCode:{}, msg :{}", strCardNo, errorCode, msg);
                break;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                log.error("异常 , cardNo:{}, errorCode:{}, msg :{}", strCardNo, errorCode, msg);
                break;
            } else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {
                String cardNo = new String(struCardRecord.byCardNo).trim();
                if(strCardNo.equals(cardNo)){
                    msg = "success";
                }
                continue;
            }
            else if(dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                log.error("卡联通完成 cardNo:{}", strCardNo);
                break;
            }

        }
        return stopRemoteConfig(msg,loginHandler,"get card");
    }

    /**
     * 长连接关闭失败，不记入失败
     * @param msg
     * @param lHandler
     * @param operation
     * @return
     */
    private String stopRemoteConfig(String msg, int lHandler, String operation) {
        if (!"success".equals(msg)) {
            msg += "\n";
        }
        if (!hCNetSDK.NET_DVR_StopRemoteConfig(lHandler)) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            log.info("fail, operation:{}, errorCode:{}, msg :{}", operation, errorCode, hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode)));
        } else {
            log.info("success  operation:{}", operation);
        }
        return msg;
    }


}
