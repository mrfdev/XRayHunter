package dk.lockfuglsang.xrayhunter.coreprotect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.jspecify.annotations.NonNull;

/**
 * Common code across CoreProtect versions.
 */
@SuppressWarnings("unused")
public class AbstractCoreProtectAdaptor {
    protected static @NonNull List<String> convertMats(List<Material> matList) {
        final List<String> result = new ArrayList<>(matList != null ? matList.size() : 0);
        if (matList != null) {
            for (final Material i : matList) {
                result.add(i.getKey().getKey().toLowerCase());
            }
        }
        return result;
    }

    protected static @NonNull List<String> convertMatsName(List<Material> matList) {
        final List<String> result = new ArrayList<>(matList != null ? matList.size() : 0);
        if (matList != null) {
            for (final Material i : matList) {
                result.add(i.name().toLowerCase());
            }
        }
        return result;
    }

    private static @NonNull List<String> convert(List<Integer> integerList) {
        final List<String> result = new ArrayList<>(integerList != null ? integerList.size() : 0);
        if (integerList != null) {
            for (final Integer i : integerList) {
                result.add("" + i);
            }
        }
        return result;
    }

    protected Class<?> getLookupClass() {
        try {
            return Class.forName("net.coreprotect.database.Lookup");
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }


    protected boolean isVersionLaterThan(String version) {
        final Pattern versionPattern = Pattern.compile("v?(?<major>[0-9]+)\\.(?<minor>[0-9]+).*");
        final Matcher m1 = versionPattern.matcher(version);
        final Matcher m2 = versionPattern.matcher("21.1");
        if (m1.matches() && m2.matches()) {
            final int major1 = Integer.parseInt(m1.group("major"), 10);
            final int major2 = Integer.parseInt(m2.group("major"), 10);
            return major1 >= major2;
        }
        return false;
    }
}
