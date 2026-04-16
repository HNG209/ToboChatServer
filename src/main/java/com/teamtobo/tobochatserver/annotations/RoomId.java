package com.teamtobo.tobochatserver.annotations;
import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RoomId {
}
