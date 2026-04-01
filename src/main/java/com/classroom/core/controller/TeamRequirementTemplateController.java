package com.classroom.core.controller;

import com.classroom.core.dto.ErrorResponse;
import com.classroom.core.dto.team.ApplyTeamRequirementTemplateRequest;
import com.classroom.core.dto.team.CreateTeamRequirementTemplateRequest;
import com.classroom.core.dto.team.TeamRequirementTemplateDto;
import com.classroom.core.dto.team.TemplateApplyResultDto;
import com.classroom.core.dto.team.UpdateTeamRequirementTemplateRequest;
import com.classroom.core.security.UserPrincipal;
import com.classroom.core.service.TeamRequirementTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/team-requirement-templates")
@Tag(name = "Team Requirement Templates", description = "Templates for team requirements")
@RequiredArgsConstructor
public class TeamRequirementTemplateController {

    private final TeamRequirementTemplateService templateService;

    @GetMapping
    @Operation(
            summary = "List team requirement templates",
            operationId = "listTeamRequirementTemplates",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Template list",
                            content = @Content(schema = @Schema(implementation = TeamRequirementTemplateDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<List<TeamRequirementTemplateDto>> listTemplates(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {

        List<TeamRequirementTemplateDto> result = templateService.listTemplates(courseId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(
            summary = "Create team requirement template (teacher only)",
            operationId = "createTeamRequirementTemplate",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Template created",
                            content = @Content(schema = @Schema(implementation = TeamRequirementTemplateDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamRequirementTemplateDto> createTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateTeamRequirementTemplateRequest request) {

        TeamRequirementTemplateDto result = templateService.createTemplate(courseId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{templateId}")
    @Operation(
            summary = "Get team requirement template",
            operationId = "getTeamRequirementTemplate",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Template details",
                            content = @Content(schema = @Schema(implementation = TeamRequirementTemplateDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamRequirementTemplateDto> getTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID templateId) {

        TeamRequirementTemplateDto result = templateService.getTemplate(courseId, templateId, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{templateId}")
    @Operation(
            summary = "Update team requirement template (teacher only)",
            operationId = "updateTeamRequirementTemplate",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Template updated",
                            content = @Content(schema = @Schema(implementation = TeamRequirementTemplateDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TeamRequirementTemplateDto> updateTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateTeamRequirementTemplateRequest request) {

        TeamRequirementTemplateDto result = templateService.updateTemplate(courseId, templateId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{templateId}")
    @Operation(
            summary = "Archive team requirement template (teacher only)",
            operationId = "archiveTeamRequirementTemplate",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Template archived"),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<Void> archiveTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID templateId) {

        templateService.archiveTemplate(courseId, templateId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{templateId}/apply")
    @Operation(
            summary = "Apply template to a task post (teacher only)",
            operationId = "applyTeamRequirementTemplate",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Template applied",
                            content = @Content(schema = @Schema(implementation = TemplateApplyResultDto.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<TemplateApplyResultDto> applyTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID templateId,
            @Valid @RequestBody ApplyTeamRequirementTemplateRequest request) {

        TemplateApplyResultDto result = templateService.applyTemplate(courseId, templateId, request, principal.getId());
        return ResponseEntity.ok(result);
    }
}
