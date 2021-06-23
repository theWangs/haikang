package com.m.hk.custom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: meng
 * @Date: 2021-05-26 11:04
 */
@RestController
@Slf4j
@RequestMapping("haikang")
public class HKFaceController {

    @Autowired
    private HKFaceCardService faceCardService;
    @Autowired
    private HKCardFaceService cardFaceService;
    @Autowired
    private HKEventService hkEventService;

    @PostMapping("/login")
    public String testLogin(String ip, String username, String passwd) throws Exception {
        HKLoginService.login(ip, username, passwd);
        Integer login = HKLoginService.loginHandleMap.get(ip);
        return login.toString();
    }

    @PostMapping("/logout")
    public String testLogout(int login) throws Exception {
        HKLoginService.loginOut(login);
        return "success";
    }

    @PostMapping("/getAbility")
    public String getAbility(Integer login) throws Exception {
        return faceCardService.getAbility(login);
    }

    @PostMapping("/insertUser")
    public String testInsert(String userId, Integer login, String username) throws Exception {
        return faceCardService.addUser(login, userId, username);
    }

    @PostMapping("/insertFace")
    public String insertFace(String userId, Integer login, MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        return faceCardService.addFace(login, userId, bytes);
    }

    @PostMapping("/insertCard")
    public String insertFaceCard(String userId, Integer login, String cardNo) throws Exception {
        return faceCardService.addCard(login, userId, cardNo);
    }

    @PostMapping("/deleteUser")
    public String deleteUser(String userId, Integer login) throws Exception {
        return faceCardService.deleteUser(login, userId);
    }

    @PostMapping("/getUser")
    public String getUser(String userId, Integer login) throws Exception {
        return faceCardService.getUser(login, userId);
    }


    @PostMapping("card/insertCard")
    public String insertCard(String cardNo, Integer login, String name) throws Exception {
        return cardFaceService.addCard(login, cardNo, name);
    }

    @PostMapping("card/insertFace")
    public String insertCardFace(String cardNo, Integer login, MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        return cardFaceService.addFace(login, cardNo, bytes);
    }

    @PostMapping("card/deleteCard")
    public String deleteCard(String cardNo, Integer login) throws Exception {
        return cardFaceService.deleteCard(login, cardNo);
    }

    @PostMapping("card/getCard")
    public String getCardNo(String cardNo, Integer login) throws Exception {
        return cardFaceService.getCard(login, cardNo);
    }

    @PostMapping("subscribe")
    public String subscribe(Integer login) throws Exception {
        return hkEventService.startSubscribe(login);
    }

    @PostMapping("unsubscribe")
    public String insertCard(Integer subscribeHandler) throws Exception {
        hkEventService.stopSubscribe(subscribeHandler);
        return "success";
    }


}
