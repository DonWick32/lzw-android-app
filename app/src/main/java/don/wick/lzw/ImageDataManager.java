package don.wick.lzw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ImageDataManager {
    private static ImageDataManager instance;
    private HashMap<String, ArrayList<int[][]>> imageDataMap = new HashMap<>();

    private HashMap<String, List<List<Integer>>> compressedDataMap = new HashMap<>();

    private ImageDataManager() {}

    public static synchronized ImageDataManager getInstance() {
        if (instance == null) {
            instance = new ImageDataManager();
        }
        return instance;
    }

    public String saveImageData(ArrayList<int[][]> matrices) {
        String id = UUID.randomUUID().toString();
        imageDataMap.put(id, matrices);
        return id;
    }

    public ArrayList<int[][]> getImageData(String id) {
        return imageDataMap.remove(id);
    }

    public String saveCompressedData(List<List<Integer>> compressedOutputs) {
        String id = UUID.randomUUID().toString();
        compressedDataMap.put(id, compressedOutputs);
        return id;
    }

    public List<List<Integer>> getCompressedData(String id) {
        return compressedDataMap.remove(id);
    }
}
