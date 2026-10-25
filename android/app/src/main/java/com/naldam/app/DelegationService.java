package com.naldam.app;

import com.google.androidbrowserhelper.locationdelegation.LocationDelegationExtraCommandHandler;

/** 날담 화면(크롬)에서 위치를 물으면 앱이 대신 폰 위치 권한을 받아서 알려줘요. */
public class DelegationService extends com.google.androidbrowserhelper.trusted.DelegationService {
    public DelegationService() {
        registerExtraCommandHandler(new LocationDelegationExtraCommandHandler());
    }
}
