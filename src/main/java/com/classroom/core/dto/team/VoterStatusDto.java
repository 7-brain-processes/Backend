package com.classroom.core.dto.team;

import com.classroom.core.dto.auth.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class VoterStatusDto {
    private UserDto user;
    private boolean hasVoted;
}
