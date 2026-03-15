package com.teamtobo.tobochatserver.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;

@Service
@RequiredArgsConstructor
public class CognitoHelper {
    @Value("${aws.cognito.userPoolId}")
    private String userPoolId;
    private final CognitoIdentityProviderClient cognitoClient;

    public void syncNameToCognito(String userId, String newName) {
        AttributeType nameAttr = AttributeType.builder().name("name").value(newName).build();
        AdminUpdateUserAttributesRequest request = AdminUpdateUserAttributesRequest.builder()
                .userPoolId(userPoolId)
                .username(userId)
                .userAttributes(nameAttr)
                .build();
        cognitoClient.adminUpdateUserAttributes(request);
    }
}
