/**
 * 类型描述
 *
 * @author chengsheng@qbb6.com
 * @date 2018/10/26 下午10:26
 */
public class TypeDescription {

    private String type;

    private String description;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TypeDescription(String type) {
        this.type = type;
    }

    public TypeDescription(String type, String description) {
        this.type = type;
        this.description = description;
    }
}
