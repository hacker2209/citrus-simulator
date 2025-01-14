package org.citrusframework.simulator.service.impl;

import org.citrusframework.TestAction;
import org.citrusframework.TestCase;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.simulator.model.ScenarioAction;
import org.citrusframework.simulator.model.ScenarioExecution;
import org.citrusframework.simulator.repository.ScenarioActionRepository;
import org.citrusframework.simulator.service.ScenarioExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ScenarioActionServiceImplTest {

    @Mock
    private ScenarioActionRepository scenarioActionRepositoryMock;

    @Mock
    private ScenarioExecutionService scenarioExecutionServiceMock;

    @Mock
    private TimeProvider timeProviderMock;

    private ScenarioAction scenarioActionWithScenarioExecution;

    private ScenarioActionServiceImpl fixture;

    private static TestCase getTestCaseWithScenarioExecutionIdVariableDefinition() {
        TestCase testCase = mock(TestCase.class);
        Map<String, Object> variableDefinitions = Map.of(ScenarioExecution.EXECUTION_ID, "1234");
        doReturn(variableDefinitions).when(testCase).getVariableDefinitions();
        return testCase;
    }

    private static TestAction getTestActionWithName(String toBeReturned) {
        TestAction testAction = mock(TestAction.class);
        doReturn(toBeReturned).when(testAction).getName();
        return testAction;
    }

    @BeforeEach
    void beforeEachSetup()  {
        ScenarioAction scenarioAction = new ScenarioAction();
        ScenarioExecution scenarioExecution = ScenarioExecution.builder()
            .startDate(Instant.now())
            .endDate(Instant.now())
            .scenarioName("scenario-name")
            .errorMessage("error-message")
            .build();
        scenarioAction.setScenarioExecution(scenarioExecution);
        scenarioActionWithScenarioExecution = spy(scenarioAction);

        fixture = new ScenarioActionServiceImpl(scenarioActionRepositoryMock, scenarioExecutionServiceMock);
        ReflectionTestUtils.setField(fixture, "timeProvider", timeProviderMock, TimeProvider.class);
    }

    @Test
    void testSave() {
        doReturn(scenarioActionWithScenarioExecution).when(scenarioActionRepositoryMock).save(scenarioActionWithScenarioExecution);

        ScenarioAction savedScenarioAction = fixture.save(scenarioActionWithScenarioExecution);
        assertEquals(scenarioActionWithScenarioExecution, savedScenarioAction);
    }

    @Test
    void testFindAll() {
        Pageable pageable = Pageable.unpaged();
        Page<ScenarioAction> page = new PageImpl<>(List.of(scenarioActionWithScenarioExecution));

        doReturn(page).when(scenarioActionRepositoryMock).findAllWithToOneRelationships(pageable);

        Page<ScenarioAction> result = fixture.findAll(pageable);

        assertEquals(page, result);

        verifyDtoPreparations();
    }

    @Test
    void testFindOne() {
        Long scenarioActionId = 1L;

        doReturn(Optional.of(scenarioActionWithScenarioExecution)).when(scenarioActionRepositoryMock).findOneWithEagerRelationships(scenarioActionId);

        Optional<ScenarioAction> maybeScenarioAction = fixture.findOne(scenarioActionId);

        assertTrue(maybeScenarioAction.isPresent());
        assertEquals(scenarioActionWithScenarioExecution, maybeScenarioAction.get());

        verifyDtoPreparations();
    }

    private void verifyDtoPreparations() {
        ArgumentCaptor<ScenarioExecution> scenarioExecutionArgumentCaptor = ArgumentCaptor.forClass(ScenarioExecution.class);
        verify(scenarioActionWithScenarioExecution).setScenarioExecution(scenarioExecutionArgumentCaptor.capture());

        ScenarioExecution expectedScenarioExecution = scenarioActionWithScenarioExecution.getScenarioExecution();
        ScenarioExecution capturedScenarioExecution = scenarioExecutionArgumentCaptor.getValue();

        assertEquals(expectedScenarioExecution.getScenarioName(), capturedScenarioExecution.getScenarioName());
    }

    @Nested
    class CreateForScenarioExecutionAndSave {

        @Test
        void createsAndSavesScenarioAction() {
            TestCase testCase = getTestCaseWithScenarioExecutionIdVariableDefinition();
            TestAction testAction = getTestActionWithName("test-action");

            ScenarioExecution mockScenarioExecution = mock(ScenarioExecution.class);
            doReturn(Optional.of(mockScenarioExecution)).when(scenarioExecutionServiceMock).findOneLazy(anyLong());

            ScenarioAction createdScenarioAction = fixture.createForScenarioExecutionAndSave(testCase, testAction);

            assertNotNull(createdScenarioAction);
            assertEquals(testAction.getName(), createdScenarioAction.getName());

            verify(mockScenarioExecution).addScenarioAction(createdScenarioAction);

            verify(scenarioExecutionServiceMock).save(mockScenarioExecution);
        }

        @Test
        void skipsCreateVariablesSteps() {
            TestCase testCase = mock(TestCase.class);
            TestAction testAction = getTestActionWithName("create-variables");

            fixture.createForScenarioExecutionAndSave(testCase, testAction);

            verifyNoInteractions(testCase);

            verifyNoInteractions(scenarioExecutionServiceMock);
            verifyNoInteractions(timeProviderMock);
        }
    }

    @Nested
    class CompleteTestAction {

        @Test
        void completesScenarioAction() {
            String testActionName = "test-action";

            TestCase testCase = getTestCaseWithScenarioExecutionIdVariableDefinition();
            TestAction testAction = getTestActionWithName(testActionName);

            ScenarioExecution mockScenarioExecution = mock(ScenarioExecution.class);
            Set<ScenarioAction> scenarioActions = Set.of(ScenarioAction.builder().name(testActionName).build());
            doReturn(scenarioActions).when(mockScenarioExecution).getScenarioActions();

            doReturn(Optional.of(mockScenarioExecution)).when(scenarioExecutionServiceMock).findOneLazy(anyLong());

            Instant mockTime = Instant.now();
            doReturn(mockTime).when(timeProviderMock).getTimeNow();

            fixture.completeTestAction(testCase, testAction);

            ScenarioAction lastScenarioAction = scenarioActions.iterator().next();
            assertEquals(mockTime, lastScenarioAction.getEndDate());

            verify(scenarioActionRepositoryMock).save(lastScenarioAction);
        }

        @Test
        void skipsCreateVariablesSteps() {
            TestCase testCase = mock(TestCase.class);
            TestAction testAction = getTestActionWithName("create-variables");

            fixture.completeTestAction(testCase, testAction);

            verifyNoInteractions(testCase);

            verifyNoInteractions(scenarioActionRepositoryMock);
            verifyNoInteractions(scenarioExecutionServiceMock);
            verifyNoInteractions(timeProviderMock);
        }

        @Test
        void withNoScenarioExecutionFound() {
            TestCase testCase = getTestCaseWithScenarioExecutionIdVariableDefinition();
            TestAction testAction = getTestActionWithName("test-action");

            doReturn(Optional.empty()).when(scenarioExecutionServiceMock).findOneLazy(1234L);

            fixture.completeTestAction(testCase, testAction);

            verifyNoInteractions(scenarioActionRepositoryMock);
            verifyNoInteractions(timeProviderMock);
        }

        @Test
        void withNoActionFound() {
            TestCase testCase = getTestCaseWithScenarioExecutionIdVariableDefinition();
            TestAction testAction = getTestActionWithName("test-action");

            ScenarioExecution mockScenarioExecution = mock(ScenarioExecution.class);
            doReturn(new HashSet<>()).when(mockScenarioExecution).getScenarioActions();
            doReturn(Optional.of(mockScenarioExecution)).when(scenarioExecutionServiceMock).findOneLazy(1234L);

            CitrusRuntimeException thrownException = assertThrows(
                CitrusRuntimeException.class,
                () -> fixture.completeTestAction(testCase, testAction)
            );
            assertEquals("No test action found with name " + testAction.getName(), thrownException.getMessage());
        }
    }
}
