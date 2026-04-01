package com.classroom.core.dto.team;

import com.classroom.core.dto.auth.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class StudentDistributedGradeDto {
    private UserDto student;
    private Integer grade;
}
