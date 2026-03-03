package com.imyvm.hoki.config;

import com.typesafe.config.*;  // CHECKSTYLE SUPPRESS: AvoidStarImport
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Option<T> {
    @NotNull private final String key;
    @NotNull private final T defaultValue;
    @Nullable private T value;
    @Nullable private final List<String> comments;

    @NotNull private final BiFunction<Config, String, T> deserializer;
    @NotNull private final Function<T, ConfigValue> serializer;

    // CHECKSTYLE SUPPRESS: DeclarationOrder
    @NotNull public final Event<OptionChangeCallback<T>> changeEvents =
        EventFactory.createArrayBacked(
            OptionChangeCallback.class,
            (listeners) -> (option, oldValue, newValue) -> {
                for (OptionChangeCallback<T> listener : listeners)
                    listener.onChange(option, oldValue, newValue);
            });

    public Option(@NotNull String key, @NotNull T defaultValue, @Nullable String comment,
                  @NotNull BiFunction<Config, String, T> deserializer, @NotNull Function<T, ConfigValue> serializer) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.comments = comment == null ? null : Arrays.asList(comment.split("\n"));
        this.deserializer = deserializer;
        this.serializer = serializer;
    }

    public Option(@NotNull String key, @NotNull T defaultValue, @Nullable String comment,
                  @NotNull BiFunction<Config, String, T> deserializer) {
        this(key, defaultValue, comment, deserializer, ConfigValueFactory::fromAnyRef);
    }

    public Option(@NotNull String key, @NotNull T defaultValue, @NotNull BiFunction<Config, String, T> deserializer) {
        this(key, defaultValue, null, deserializer, ConfigValueFactory::fromAnyRef);
    }

    boolean loadFromConfig(Config config) {
        try {
            this.setValue(this.deserializer.apply(config, this.key));
            return true;
        } catch (ConfigException.Missing e) {
            this.setValue(null);
            return false;
        }
    }

    Config saveToConfig(Config config) {
        T saveValue = Objects.requireNonNullElse(this.value, this.defaultValue);
        ConfigOrigin commentedOrigin = ConfigOriginFactory.newSimple().withComments(this.comments);
        ConfigValue value = this.serializer.apply(saveValue).withOrigin(commentedOrigin);

        return config.withValue(this.key, value);
    }

    @NotNull
    public T getValue() {
        return Objects.requireNonNullElse(this.value, this.defaultValue);
    }

    public void setValue(@Nullable T value) {
        if (this.value != value) {
            T oldValue = this.value;
            this.value = value;
            this.changeEvents.invoker().onChange(this, oldValue, value);
        }
    }

    @NotNull
    public String getKey() {
        return this.key;
    }

    @FunctionalInterface
    public interface OptionChangeCallback<T> {
        void onChange(@NotNull Option<T> option, @Nullable T oldValue, @Nullable T newValue);
    }
}
