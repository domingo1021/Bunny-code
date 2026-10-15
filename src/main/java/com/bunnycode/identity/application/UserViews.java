package com.bunnycode.identity.application;

import com.bunnycode.identity.domain.UserAccount;
import com.bunnycode.storage.FileStorage;

final class UserViews {

    private UserViews() {
    }

    static UserProfileView privateView(UserAccount user, FileStorage storage) {
        return new UserProfileView(user.getId(), user.getName(), user.getEmail(),
                storage.publicUrl(user.getPictureKey()), user.getProfile(), user.getLevel());
    }

    static UserProfileView publicView(UserAccount user, FileStorage storage) {
        return new UserProfileView(user.getId(), user.getName(), null,
                storage.publicUrl(user.getPictureKey()), user.getProfile(), user.getLevel());
    }
}
