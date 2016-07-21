

public class Test {
  static class Sites {
    CachedMethod some_method;
    
    static final Sites INSTANCE = new Sites(); 
  }
  
  public static Sites sites(Context context) {
    return Sites.INSTANCE;
  }
  
  public static void main(String[] args) {
    Context context = null;
    Object caller = null;
    Object target = null;
    Object result = sites(context).some_method.call(context, caller, target, "arg1", "arg2"); 
    System.out.println(result);
  }
}
