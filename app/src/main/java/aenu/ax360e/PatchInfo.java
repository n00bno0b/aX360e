// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single patch entry within a game's patch file
 */
public class PatchInfo {
    public static class PatchDataEntry {
        public String dataType; // be8, be16, be32, f32, etc.
        public String address;
        public String value;

        public PatchDataEntry(String dataType, String address, String value) {
            this.dataType = dataType;
            this.address = address;
            this.value = value;
        }
    }

    public static class GamePatchFile {
        public String titleId;
        public String titleName;
        public String filePath;
        public String hash;
        public List<PatchInfo> patches;

        public GamePatchFile() {
            patches = new ArrayList<>();
        }
    }

    // Individual patch fields
    public String patchName;
    public String description;
    public String author;
    public boolean isEnabled;
    public List<PatchDataEntry> dataEntries;

    // Parent file reference
    public String titleId;
    public String titleName;
    public String filePath;

    public PatchInfo() {
        dataEntries = new ArrayList<>();
        isEnabled = false;
    }

    public int getDataEntryCount() {
        return dataEntries.size();
    }

    public String getShortDescription() {
        if (description != null && !description.isEmpty()) {
            return description.length() > 100 ?
                description.substring(0, 97) + "..." : description;
        }
        return "No description";
    }
}
