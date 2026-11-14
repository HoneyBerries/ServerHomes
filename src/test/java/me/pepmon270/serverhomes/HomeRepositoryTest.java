package me.pepmon270.serverhomes;

import me.pepmon270.serverhomes.HomeRepository.RenameResult;
import me.pepmon270.serverhomes.HomeRepository.SetResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeRepositoryTest {

    @NotNull
    private final UUID alice = UUID.randomUUID();
    @NotNull
    private final UUID bob = UUID.randomUUID();
    @Nullable
    private HomeRepository repository;

    @BeforeEach
    void open(@TempDir @NotNull Path dir) throws Exception {
        repository = new HomeRepository(dir.resolve("homes.db"));
    }

    @AfterEach
    void close() {
        repository.close();
    }

    @NotNull
    private static Home home(@NotNull String name) {
        return new Home(name, "world", 1.5, 64, -2.5, 90f, 10f);
    }

    @Test
    void savesAndFindsHome() {
        assertEquals(SetResult.CREATED, repository.set(alice, home("base"), 5).join());
        assertEquals(home("base"), repository.find(alice, "base").join().orElseThrow());
        assertTrue(repository.find(bob, "base").join().isEmpty());
    }

    @Test
    void overwriteIsAllowedAtLimit() {
        repository.set(alice, home("a"), 1).join();
        assertEquals(SetResult.LIMIT_REACHED, repository.set(alice, home("b"), 1).join());
        assertEquals(SetResult.UPDATED, repository.set(alice, home("a"), 1).join());
        assertEquals(1, repository.list(alice).join().size());
    }

    @Test
    void negativeLimitIsUnlimited() {
        for (int i = 0; i < 20; i++) {
            assertEquals(SetResult.CREATED, repository.set(alice, home("h" + i), -1).join());
        }
    }

    @Test
    void deleteReportsWhetherAnythingWasRemoved() {
        repository.set(alice, home("a"), 5).join();
        assertTrue(repository.delete(alice, "a").join());
        assertFalse(repository.delete(alice, "a").join());
    }

    @Test
    void renameHandlesMissingAndExistingTargets() {
        repository.set(alice, home("a"), 5).join();
        repository.set(alice, home("b"), 5).join();
        assertEquals(RenameResult.NOT_FOUND, repository.rename(alice, "x", "y").join());
        assertEquals(RenameResult.TARGET_EXISTS, repository.rename(alice, "a", "b").join());
        assertEquals(RenameResult.RENAMED, repository.rename(alice, "a", "c").join());
        assertEquals(List.of("b", "c"), repository.list(alice).join().stream().map(Home::name).toList());
    }
}
