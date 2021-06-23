package com.m.hk.custom;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.m.hk.lib.HCNetSDK;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * 明眸以人为中心
 *
 * @Author: meng
 * @Date: 2021-05-26 10:51
 */
@Slf4j
@Component
public class HKFaceCardService {

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

    public String addUser(int loginHandler, String strEmployeeID, String inputName) {
        String msg = "success";
        HCNetSDK.BYTE_ARRAY ptrByteArray = new HCNetSDK.BYTE_ARRAY(1024);    //数组
        String strInBuffer = "POST /ISAPI/AccessControl/UserInfo/Record?format=json";
        System.arraycopy(strInBuffer.getBytes(), 0, ptrByteArray.byValue, 0, strInBuffer.length());//字符串拷贝到数组中
        ptrByteArray.write();

        int lHandler = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, 2550/*NET_DVR_JSON_CONFIG*/, ptrByteArray.getPointer(), strInBuffer.length(), null, null);
        if (lHandler < 0) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.error("fail userId:{},name:{}, errorCode:{}, msg :{}", strEmployeeID, inputName, errorCode, msg);
            return msg;
        } else {
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

            //将中文字符编码之后用数组拷贝的方式，避免因为编码导致的长度问题
            String strInBuffer1 = "{\"UserInfo\":{\"Valid\":{\"beginTime\":\"2020-02-02T02:02:02\",\"enable\":true,\"endTime\":\"2035-08-01T17:30:08\"},\"checkUser\":false,\"doorRight\":\"1\",\"RightPlan\":[{\"doorNo\": 1,\"planTemplateNo\": \"1\"}],\"employeeNo\":\""
                    + strEmployeeID + "\",\"name\":\"";
            String strInBuffer2 = "\",\"userType\":\"normal\"}}";
            int iStringSize = nameByte.length + strInBuffer1.length() + strInBuffer2.length();

            HCNetSDK.BYTE_ARRAY ptrByte = new HCNetSDK.BYTE_ARRAY(iStringSize);
            System.arraycopy(strInBuffer1.getBytes(), 0, ptrByte.byValue, 0, strInBuffer1.length());
            System.arraycopy(nameByte, 0, ptrByte.byValue, strInBuffer1.length(), nameByte.length);
            System.arraycopy(strInBuffer2.getBytes(), 0, ptrByte.byValue, strInBuffer1.length() + nameByte.length, strInBuffer2.length());
            ptrByte.write();

            System.out.println(new String(ptrByte.byValue));

            HCNetSDK.BYTE_ARRAY ptrOutuff = new HCNetSDK.BYTE_ARRAY(1024);

            IntByReference pInt = new IntByReference(0);
            while (true) {
                int dwState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(lHandler, ptrByte.getPointer(), iStringSize, ptrOutuff.getPointer(), 1024, pInt);
                //读取返回的json并解析
                ptrOutuff.read();
                String strResult = new String(ptrOutuff.byValue).trim();
                JSONObject jsonResult = JSONObject.parseObject(strResult);
                int statusCode = jsonResult.getIntValue("statusCode");
                String statusString = jsonResult.getString("statusString");
                if (dwState == -1) {
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.error("fail，userId:{},name:{}, errorCode:{}, msg :{}", strEmployeeID, inputName, errorCode, msg);
                    break;
                }
                //配 置 等 待 ， 客 户 端 可 重 新 NET_DVR_SendWithRecvRemoteConfig
                else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_NEEDWAIT) {
                    log.info(" 配置等待");
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        log.error("wait error");
                    }
                    continue;
                }
                //配 置 失 败 ， 客 户 端 可 重 新NET_DVR_SendWithRecvRemoteConfig 下发下一条
                else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                    msg = jsonResult.toString();
                    log.info("失败 userId:{},name:{}, return:{}", strEmployeeID, inputName, jsonResult);
                    break;
                }
                //配 置 异 常 ， 此 时 客 户 端 可 调 用 NET_DVR_StopRemoteConfig 结束
                else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                    msg = jsonResult.toString();
                    log.info("异常 userId:{},name:{}, return:{}", strEmployeeID, inputName, jsonResult);
                    break;
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {//返回NET_SDK_CONFIG_STATUS_SUCCESS代表流程走通了，但并不代表下发成功，比如有些设备可能因为人员已存在等原因下发失败，所以需要解析Json报文
                    if (statusCode != 1) {
                        msg = jsonResult.toString();
                        log.error("接口通但异常 userId:{},name:{}, return:{}", strEmployeeID, inputName, jsonResult);
                    } else {
                        log.info("success return:{}", jsonResult);
                    }
                    break;
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                    //下发人员时：dwState其实不会走到这里，因为设备不知道我们会下发多少个人，所以长连接需要我们主动关闭
                    log.info("finish ");
                    break;
                }
            }
            msg = stopRemoteConfig(msg, lHandler, "add User");
        }
        return msg;
    }

    public String addFace(int loginHandler, String personId, byte[] faceByte) {
        String msg = "success";
        HCNetSDK.BYTE_ARRAY ptrByteArray = new HCNetSDK.BYTE_ARRAY(1024);    //数组
        String strInBuffer = "POST /ISAPI/Intelligent/FDLib/FaceDataRecord?format=json ";
        System.arraycopy(strInBuffer.getBytes(), 0, ptrByteArray.byValue, 0, strInBuffer.length());//字符串拷贝到数组中
        ptrByteArray.write();

        int lHandler = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, 2551/*NET_DVR_FACE_DATA_RECORD*/, ptrByteArray.getPointer(), strInBuffer.length(), null, null);
        if (lHandler < 0) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("fail， userId:{}, errorCode:{}, msg :{}", personId, errorCode, msg);
            return msg;
        } else {
            //批量下发多个人脸（不同工号）
            HCNetSDK.NET_DVR_JSON_DATA_CFG[] struAddFaceDataCfg = (HCNetSDK.NET_DVR_JSON_DATA_CFG[]) new HCNetSDK.NET_DVR_JSON_DATA_CFG().toArray(1);
            struAddFaceDataCfg[0].read();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("faceLibType", "blackFD");
            jsonObject.put("FDID", "1");
            jsonObject.put("FPID", personId);//人脸下发关联的工号

            String strJsonData = jsonObject.toString();

            System.arraycopy(strJsonData.getBytes(), 0, ptrByteArray.byValue, 0, strJsonData.length());//字符串拷贝到数组中
            ptrByteArray.write();

            struAddFaceDataCfg[0].dwSize = struAddFaceDataCfg[0].size();
            struAddFaceDataCfg[0].lpJsonData = ptrByteArray.getPointer();
            struAddFaceDataCfg[0].dwJsonDataSize = strJsonData.length();


            HCNetSDK.BYTE_ARRAY ptrpicByte = new HCNetSDK.BYTE_ARRAY(faceByte.length);
            ptrpicByte.byValue = faceByte;
            ptrpicByte.write();
            struAddFaceDataCfg[0].dwPicDataSize = faceByte.length;
            struAddFaceDataCfg[0].lpPicData = ptrpicByte.getPointer();
            struAddFaceDataCfg[0].write();

            HCNetSDK.BYTE_ARRAY ptrOutuff = new HCNetSDK.BYTE_ARRAY(1024);
            IntByReference pInt = new IntByReference(0);

            int dwState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(lHandler, struAddFaceDataCfg[0].getPointer(), struAddFaceDataCfg[0].dwSize, ptrOutuff.getPointer(), ptrOutuff.size(), pInt);
            //读取返回的json并解析
            ptrOutuff.read();
            String strResult = new String(ptrOutuff.byValue).trim();
            if (strResult.isEmpty()) {
                msg = "add Face error no response , please check";
                log.error("error no response userId:{}", personId);
            } else {
                JSONObject jsonResult = JSONObject.parseObject(strResult);
                int statusCode = jsonResult.getIntValue("statusCode");
                if (dwState == -1) {
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.info("fail，userId:{}, errorCode:{}, msg :{}", personId, errorCode, msg);
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                    msg = jsonResult.toString();
                    log.info("下发人脸失败 ，userId:{},  msg :{}", personId, msg);
                    //可以继续下发下一个
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                    msg = jsonResult.toString();
                    log.info("下发人脸异常 ，userId:{},  msg :{}", personId, msg);
                    //异常是长连接异常，不能继续下发后面的数据，需要重新建立长连接
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {
                    //返回NET_SDK_CONFIG_STATUS_SUCCESS代表流程走通了，但并不代表下发成功，比如人脸图片不符合设备规范等原因，所以需要解析Json报文
                    if (statusCode != 1) {
                        msg = jsonResult.toString();
                        log.info("下发人脸成功,但是有异常情况 ，userId:{},  msg :{}", personId, msg);
                    } else {
                        log.info("下发人脸成功 ，userId:{},  msg :{}", personId, jsonResult);
                    }
                    //可以继续下发下一个
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                    //下发人脸时：dwState其实不会走到这里，因为设备不知道我们会下发多少个人，所以长连接需要我们主动关闭
                    log.info("finish ");
                } else {
                    log.info("下发人脸识别，其他状态:{} ", dwState);
                }
            }
            msg = stopRemoteConfig(msg, lHandler, "add Face");
        }
        return msg;
    }

    /**
     * @param loginHandler 登陆句柄
     * @param personId     employeeNo
     * @param cardNo       卡号
     * @return
     */
    public String addCard(int loginHandler, String personId, String cardNo) {
        String msg = "success";
        HCNetSDK.BYTE_ARRAY ptrByteArray = new HCNetSDK.BYTE_ARRAY(1024);    //数组
        String strInBuffer = "PUT /ISAPI/AccessControl/CardInfo/SetUp?format=json ";
        System.arraycopy(strInBuffer.getBytes(), 0, ptrByteArray.byValue, 0, strInBuffer.length());//字符串拷贝到数组中
        ptrByteArray.write();

        int lHandler = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, 2550/*NET_DVR_FACE_DATA_RECORD*/, ptrByteArray.getPointer(), strInBuffer.length(), null, null);
        if (lHandler < 0) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("fail，userId:{}, cardNo:{}, errorCode:{}, msg :{}", personId, cardNo, errorCode, msg);
            return msg;
        } else {
            JSONObject param = new JSONObject();
            JSONObject cardInfoJSON = new JSONObject();
            param.put("CardInfo", cardInfoJSON);
            cardInfoJSON.put("employeeNo", personId);
            cardInfoJSON.put("cardNo", cardNo);
            cardInfoJSON.put("cardType", "normalCard");
            var paramByteStr = param.toString();

            HCNetSDK.BYTE_ARRAY ptrByte = new HCNetSDK.BYTE_ARRAY(paramByteStr.length());
            System.arraycopy(paramByteStr.getBytes(), 0, ptrByte.byValue, 0, paramByteStr.length());
            ptrByte.write();

            HCNetSDK.BYTE_ARRAY ptrOutuff = new HCNetSDK.BYTE_ARRAY(1024);
            IntByReference pInt = new IntByReference(0);

            int dwState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(lHandler, ptrByte.getPointer(), ptrByte.size(), ptrOutuff.getPointer(), ptrOutuff.size(), pInt);
            //读取返回的json并解析
            ptrOutuff.read();
            String strResult = new String(ptrOutuff.byValue).trim();
            if (strResult.isEmpty()) {
                msg = "add Card error no response";
                log.error("error no response userId:{}, cardNo:{}", personId, cardNo);
            } else {
                JSONObject jsonResult = JSONObject.parseObject(strResult);
                int statusCode = jsonResult.getIntValue("statusCode");
                if (dwState == -1) {
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.info("fail，userId:{}, cardNo:{], errorCode:{}, msg :{}", personId, cardNo, errorCode, msg);
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                    msg = jsonResult.toString();
                    log.info("下发失败 ，userId:{},cardNo:{}, msg :{}", personId, cardNo, msg);
                    //可以继续下发下一个
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                    msg = jsonResult.toString();
                    log.info("下发异常 ，userId:{}, cardNo:{}, msg :{}", personId, cardNo, msg);
                    //异常是长连接异常，不能继续下发后面的数据，需要重新建立长连接
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {
                    //返回NET_SDK_CONFIG_STATUS_SUCCESS代表流程走通了，但并不代表下发成功，比如人脸图片不符合设备规范等原因，所以需要解析Json报文
                    if (statusCode != 1) {
                        msg = jsonResult.toString();
                        log.info("下发成功,但是有异常情况 ，userId:{}, cardNo:{}, msg :{}", personId, cardNo, msg);
                    } else {
                        log.info("下发成功 ，userId:{}, cardNo:{},  msg :{}", personId, cardNo, jsonResult);
                    }
                    //可以继续下发下一个
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                    //下发人脸时：dwState其实不会走到这里，因为设备不知道我们会下发多少个人，所以长连接需要我们主动关闭
                    log.info("finish ");
                } else {
                    log.info("下发，其他状态:{} ", dwState);
                }
            }
            msg = stopRemoteConfig(msg, lHandler, "add Card");
        }
        return msg;
    }

    public String getAbility(int loginHandler) {
        String msg = "success";
        String strURL = "GET /ISAPI/AccessControl/UserInfo/capabilities?format=json";
        HCNetSDK.BYTE_ARRAY ptrUrl = new HCNetSDK.BYTE_ARRAY(1024);
        System.arraycopy(strURL.getBytes(), 0, ptrUrl.byValue, 0, strURL.length());
        ptrUrl.write();

        HCNetSDK.NET_DVR_XML_CONFIG_INPUT struXMLInput = new HCNetSDK.NET_DVR_XML_CONFIG_INPUT();
        struXMLInput.read();
        struXMLInput.dwSize = struXMLInput.size();
        struXMLInput.lpRequestUrl = ptrUrl.getPointer();
        struXMLInput.dwRequestUrlLen = ptrUrl.byValue.length;
        struXMLInput.lpInBuffer = null;//ptrInBuffer.getPointer();
        struXMLInput.dwInBufferSize = 0;//ptrInBuffer.byValue.length;
        struXMLInput.write();

        HCNetSDK.BYTE_ARRAY ptrStatusByte = new HCNetSDK.BYTE_ARRAY(4 * 4096);
        ptrStatusByte.read();

        HCNetSDK.BYTE_ARRAY ptrOutByte = new HCNetSDK.BYTE_ARRAY(1024 * 1024);
        ptrOutByte.read();

        HCNetSDK.NET_DVR_XML_CONFIG_OUTPUT struXMLOutput = new HCNetSDK.NET_DVR_XML_CONFIG_OUTPUT();
        struXMLOutput.read();
        struXMLOutput.dwSize = struXMLOutput.size();
        struXMLOutput.lpOutBuffer = ptrOutByte.getPointer();
        struXMLOutput.dwOutBufferSize = ptrOutByte.size();
        struXMLOutput.lpStatusBuffer = ptrStatusByte.getPointer();
        struXMLOutput.dwStatusSize = ptrStatusByte.size();
        struXMLOutput.write();

        if (!hCNetSDK.NET_DVR_STDXMLConfig(loginHandler, struXMLInput, struXMLOutput)) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("[HK] NET_DVR_STDXMLConfig fail ，errorCode:{}, msg :{}", errorCode, msg);
            return msg;

        } else {
            struXMLOutput.read();
            ptrOutByte.read();
            ptrStatusByte.read();
            String strOutXML = new String(ptrOutByte.byValue).trim();
            System.out.println("获取设备能力集输出结果:" + strOutXML);
            String strStatus = new String(ptrStatusByte.byValue).trim();
            System.out.println("获取设备能力集返回状态：" + strStatus);
        }
        return msg;
    }

    /**
     * 删除用户，会关联删除用户下的卡号、人脸
     * @param loginHandler
     * @param strEmployeeID
     * @return
     */
    public String deleteUser(int loginHandler, String strEmployeeID) {
        String msg = "success";
        String strURL = "PUT /ISAPI/AccessControl/UserInfoDetail/Delete?format=json";
        HCNetSDK.BYTE_ARRAY ptrUrl = new HCNetSDK.BYTE_ARRAY(1024);
        System.arraycopy(strURL.getBytes(), 0, ptrUrl.byValue, 0, strURL.length());
        ptrUrl.write();

        //输入删除条件
        HCNetSDK.BYTE_ARRAY ptrInBuffer = new HCNetSDK.BYTE_ARRAY(1024);
        ptrInBuffer.read();
        JSONObject deleteParam = new JSONObject();
        JSONObject userInfoDetail = new JSONObject();
        var employeeNoList = new JSONArray();
        JSONObject innerParam = new JSONObject();
        innerParam.put("employeeNo", strEmployeeID);
        employeeNoList.add(innerParam);
        userInfoDetail.put("mode", "byEmployeeNo");
        userInfoDetail.put("EmployeeNoList", employeeNoList);
        deleteParam.put("UserInfoDetail", userInfoDetail);
        String strInbuffer = deleteParam.toString();

        ptrInBuffer.byValue = strInbuffer.getBytes();
        ptrInBuffer.write();

        HCNetSDK.NET_DVR_XML_CONFIG_INPUT struXMLInput = new HCNetSDK.NET_DVR_XML_CONFIG_INPUT();
        struXMLInput.read();
        struXMLInput.dwSize = struXMLInput.size();
        struXMLInput.lpRequestUrl = ptrUrl.getPointer();
        struXMLInput.dwRequestUrlLen = ptrUrl.byValue.length;
        struXMLInput.lpInBuffer = ptrInBuffer.getPointer();
        struXMLInput.dwInBufferSize = ptrInBuffer.byValue.length;
        struXMLInput.write();

        HCNetSDK.BYTE_ARRAY ptrStatusByte = new HCNetSDK.BYTE_ARRAY(1024);
        ptrStatusByte.read();

        HCNetSDK.BYTE_ARRAY ptrOutByte = new HCNetSDK.BYTE_ARRAY(1024);
        ptrOutByte.read();
        HCNetSDK.NET_DVR_XML_CONFIG_OUTPUT struXMLOutput = new HCNetSDK.NET_DVR_XML_CONFIG_OUTPUT();
        struXMLOutput.read();
        struXMLOutput.dwSize = struXMLOutput.size();
        struXMLOutput.lpOutBuffer = ptrOutByte.getPointer();
        struXMLOutput.dwOutBufferSize = ptrOutByte.size();
        struXMLOutput.lpStatusBuffer = ptrStatusByte.getPointer();
        struXMLOutput.dwStatusSize = ptrStatusByte.size();
        struXMLOutput.write();

        if (!hCNetSDK.NET_DVR_STDXMLConfig(loginHandler, struXMLInput, struXMLOutput)) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("fail ，userId:{}, errorCode:{}, msg :{}", strEmployeeID, errorCode, msg);
        } else {
            struXMLOutput.read();
            ptrOutByte.read();
            ptrStatusByte.read();
            String strOutXML = new String(ptrOutByte.byValue).trim();
            log.info("success ，userId:{}, res:{}", strEmployeeID, strOutXML);
            String strStatus = new String(ptrStatusByte.byValue).trim();
        }
        return msg;
    }

    public String getUser(int loginHandler, String userId) {
        String msg = "success";
        HCNetSDK.BYTE_ARRAY ptrByteArray = new HCNetSDK.BYTE_ARRAY(1024);    //数组
        String strInBuffer = "POST /ISAPI/AccessControl/UserInfo/Search?format=json";
        System.arraycopy(strInBuffer.getBytes(), 0, ptrByteArray.byValue, 0, strInBuffer.length());//字符串拷贝到数组中
        ptrByteArray.write();

        int lHandler = hCNetSDK.NET_DVR_StartRemoteConfig(loginHandler, 2550/*NET_DVR_JSON_CONFIG*/, ptrByteArray.getPointer(), strInBuffer.length(), null, null);
        if (lHandler < 0) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.error("fail ,userId:{}, errorCode:{}, msg :{}", userId, errorCode, msg);
            return msg;
        } else {
            //组装查询的JSON报文，这边查询的是所有的卡
            JSONObject jsonObject = new JSONObject();
            JSONObject jsonSearchCond = new JSONObject();

            //如果需要查询指定的工号人员信息，把下面注释的内容去除掉即可
            JSONArray employeeNoList = new JSONArray();
            JSONObject employeeNo1 = new JSONObject();
            employeeNo1.put("employeeNo", userId);
            employeeNoList.add(employeeNo1);
            jsonSearchCond.put("EmployeeNoList", employeeNoList);

            jsonSearchCond.put("searchID", "123e4567-e89b-12d3-a456-426655440000");
            jsonSearchCond.put("searchResultPosition", 0);
            jsonSearchCond.put("maxResults", 10);
            jsonObject.put("UserInfoSearchCond", jsonSearchCond);

            String strInbuff = jsonObject.toString();

            //把string传递到Byte数组中，后续用.getPointer()方法传入指针地址中。
            HCNetSDK.BYTE_ARRAY ptrInbuff = new HCNetSDK.BYTE_ARRAY(strInbuff.length());
            System.arraycopy(strInbuff.getBytes(), 0, ptrInbuff.byValue, 0, strInbuff.length());
            ptrInbuff.write();

            //定义接收结果的结构体
            HCNetSDK.BYTE_ARRAY ptrOutuff = new HCNetSDK.BYTE_ARRAY(1024);

            IntByReference pInt = new IntByReference(0);

            while (true) {
                int dwState = hCNetSDK.NET_DVR_SendWithRecvRemoteConfig(lHandler, ptrInbuff.getPointer(), strInbuff.length(), ptrOutuff.getPointer(), 1024, pInt);
                if (dwState == -1) {
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.error("fail，userId:{}, errorCode:{}, msg :{}", userId, errorCode, msg);
                    break;
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_NEEDWAIT) {
                    System.out.println("配置等待");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        log.error("wait error");
                    }
                    continue;
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FAILED) {
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.error("查询人员失败，userId:{}, errorCode:{}, msg :{}", userId, errorCode, msg);
                    break;
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_EXCEPTION) {
                    int errorCode = hCNetSDK.NET_DVR_GetLastError();
                    msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
                    log.error("查询人员异常，userId:{}, errorCode:{}, msg :{}", userId, errorCode, msg);
                    break;
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_SUCCESS) {
                    ptrOutuff.read();
                    String res = new String(ptrOutuff.byValue).trim();
                    JSONObject jsonRes = JSON.parseObject(res);
                    int intValue = jsonRes.getJSONObject("UserInfoSearch").getIntValue("numOfMatches");
                    if (intValue == 0) {
                        msg = "no one";
                        log.info("查询人员成功，userId:{}, res:{}", userId, new String(ptrOutuff.byValue).trim());
                        break;
                    } else {
                        log.info("查询人员成功，userId:{}, res:{}", userId, new String(ptrOutuff.byValue).trim());
                        break;
                    }
                } else if (dwState == HCNetSDK.NET_SDK_CONFIG_STATUS_FINISH) {
                    log.info("获取人员完成，userId:{}", userId);
                    break;
                }
            }
            msg = stopRemoteConfig(msg, lHandler, "get User");
        }
        return msg;
    }

    private String stopRemoteConfig(String msg, int lHandler, String operation) {
        if (!"success".equals(msg)) {
            msg += "\n";
        }
        if (!hCNetSDK.NET_DVR_StopRemoteConfig(lHandler)) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            msg = hCNetSDK.NET_DVR_GetErrorMsg(new IntByReference(errorCode));
            log.info("fail, operation:{}, errorCode:{}, msg :{}", operation, errorCode, msg);
        } else {
            log.info("success  operation:{}", operation);
        }
        return msg;
    }


}
