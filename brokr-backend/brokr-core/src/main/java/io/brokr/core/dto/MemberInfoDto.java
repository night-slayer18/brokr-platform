package io.brokr.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberInfoDto {
    private String memberId;
    private String clientId;
    private String host;
    private List<TopicPartitionDto> assignment;

    public static MemberInfoDto fromDomain(io.brokr.core.model.MemberInfo memberInfo) {
        return MemberInfoDto.builder()
                .memberId(memberInfo.getMemberId())
                .clientId(memberInfo.getClientId())
                .host(memberInfo.getHost())
                .assignment(memberInfo.getAssignment().stream()
                        .map(TopicPartitionDto::fromDomain)
                        .toList())
                .build();
    }
}