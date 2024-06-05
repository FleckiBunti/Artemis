package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisExerciseChatSessionRepository;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisExerciseChatSession}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/exercise-chat/")
public class IrisExerciseChatSessionResource {

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final ExerciseRepository exerciseRepository;

    private final IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    protected IrisExerciseChatSessionResource(IrisExerciseChatSessionRepository irisExerciseChatSessionRepository, UserRepository userRepository,
            ExerciseRepository exerciseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator,
            IrisRateLimitService irisRateLimitService) {
        this.irisExerciseChatSessionRepository = irisExerciseChatSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * GET exercise-chat/{exerciseId}/sessions/current: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @PostMapping("{exerciseId}/sessions/current")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisExerciseChatSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var sessionOptional = irisExerciseChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(exercise.getId(), user.getId(), Pageable.ofSize(1)).stream()
                .findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            irisSessionService.checkHasAccessToIrisSession(session, user);
            return ResponseEntity.ok(session);
        }

        return createSessionForExercise(exerciseId);
    }

    /**
     * GET exercise-chat/{exerciseId}/sessions: Retrieve all Iris Sessions for the programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{exerciseId}/sessions")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<List<IrisExerciseChatSession>> getAllSessions(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var sessions = irisExerciseChatSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    /**
     * POST exercise-chat/{exerciseId}/session: Create a new iris session for an exercise and user.
     * If there already exists an iris session for the exercise and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("{exerciseId}/sessions")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisExerciseChatSession> createSessionForExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var session = irisExerciseChatSessionRepository.save(new IrisExerciseChatSession(programmingExercise, user));
        var uriString = "/api/iris/sessions/" + session.getId();

        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    private static ProgrammingExercise validateExercise(Exercise exercise) {
        if (!(exercise instanceof ProgrammingExercise programmingExercise)) {
            throw new ConflictException("Iris is only supported for programming exercises", "Iris", "irisProgrammingExercise");
        }
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return programmingExercise;
    }
}
