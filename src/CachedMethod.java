
public interface CachedMethod {
  Object call(Context context, Object caller, Object target, Object... args);
}
