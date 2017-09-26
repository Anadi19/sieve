package model.acp;

/**
 * Created by cygnus on 9/25/17.
 */
public class BooleanCondition {

    private String attribute;

    private RelOperator operator;

    private String value;

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public RelOperator getOperator() {
        return operator;
    }

    public void setOperator(RelOperator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public BooleanCondition(String attribute, RelOperator operator, String value) {
        this.attribute = attribute;
        this.operator = operator;
        this.value = value;
    }
}
