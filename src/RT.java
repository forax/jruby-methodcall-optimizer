import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;

public class RT {
  public static Object log(Object... args) {
    return Arrays.deepToString(args);
  }
  
  public static CallSite bsm(Lookup lookup, String name, MethodType methodType, String hello) throws NoSuchMethodException, IllegalAccessException {
    System.out.println("bootstrap method called with " + name + methodType);
    
    MethodHandle log = MethodHandles.lookup().findStatic(RT.class, "log", MethodType.methodType(Object.class, Object[].class));
    
    return new ConstantCallSite(log.asType(methodType));
  }
}
