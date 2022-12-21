package com.leok12.wirelessadbtest;

import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

public class Application extends android.app.Application {
    public Application(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            HiddenApiBypass.setHiddenApiExemptions("");
        }
    }
}
