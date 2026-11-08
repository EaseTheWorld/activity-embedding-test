package androidx.window.extensions.embedding;

import androidx.window.extensions.core.util.function.Function;

public interface ActivityEmbeddingComponent {
    ActivityStack.Token getActivityStackToken(String tag);

    ParentContainerInfo getParentContainerInfo(ActivityStack.Token token);

    void setActivityStackAttributesCalculator(
            Function<ActivityStackAttributesCalculatorParams, ActivityStackAttributes> calculator);

    void updateActivityStackAttributes(
            ActivityStack.Token token,
            ActivityStackAttributes attributes);
}
