package cn.iq99.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})    //作用于类上
@Retention(RetentionPolicy.RUNTIME)  //在运行时解析注解	
@Documented	//该注解将包含在javadoc中
public @interface PadbController {

	String value() default "";
}
