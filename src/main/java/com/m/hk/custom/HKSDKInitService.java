package com.m.hk.custom;

import com.m.hk.lib.HCNetSDK;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Author: meng
 * @Date: 2021-05-26 10:13
 */
@Slf4j
@Component
public class HKSDKInitService {

    static HCNetSDK hCNetSDK = HCNetSDK.INSTANCE;

    public void init() {
        log.info("===============");
        new Thread(()->{
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            boolean b = hCNetSDK.NET_DVR_Init();
            hCNetSDK.NET_DVR_SetConnectTime(10, 1);
            hCNetSDK.NET_DVR_SetReconnect(100, true);
            if(!b){
                log.error("=================== SDK init fail ===================");
            }else {
                log.info("============== SDK init success ====================");
            }
        }).start();

    }
}
