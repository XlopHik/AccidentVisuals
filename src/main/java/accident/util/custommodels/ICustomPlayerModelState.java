package accident.util.custommodels;

public interface ICustomPlayerModelState {
    boolean accident$hasCustomModel();

    String accident$getCustomModel();

    void accident$setCustomModel(boolean enabled, String model);
}
