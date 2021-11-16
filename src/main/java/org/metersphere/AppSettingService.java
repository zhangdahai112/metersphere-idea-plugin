package org.metersphere;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.metersphere.state.AppSettingState;

@State(name = "metersphereState", storages = {@Storage("msstore.xml")})
public class AppSettingService implements PersistentStateComponent<AppSettingState> {
    private AppSettingState appSettingState = new AppSettingState();

    @Override
    public @Nullable AppSettingState getState() {
        return appSettingState;
    }

    @Override
    public void loadState(@NotNull AppSettingState state) {
        this.appSettingState = state;
    }
}
