package com.m.hk.start;

import com.m.hk.custom.HKSDKInitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * @Author: meng
 * @Date: 2021-06-22 16:54
 */
@Component
public class MyApplicationRunner implements ApplicationRunner {

    @Autowired
    private HKSDKInitService hksdkInitService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        hksdkInitService.init();
    }
}
