package dk.lockfuglsang.xrayhunter.model;

import java.util.Comparator;
import org.jspecify.annotations.NonNull;

public class OreVeinComparator implements Comparator<OreVein> {
    @Override
    public int compare(@NonNull OreVein v1, @NonNull OreVein v2) {
        return (int) (v2.getTime() - v1.getTime());
    }
}
