package com.codingx.governance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.governance.domain.model.GovernanceProjectProfile;
import com.codingx.governance.domain.repository.GovernanceProjectProfileRepository;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceProjectProfileDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernanceProjectProfileMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 项目画像仓储实现，负责保存工作空间扫描结果和查询最近画像。
 */
@Repository
@RequiredArgsConstructor
public class GovernanceProjectProfileRepositoryImpl implements GovernanceProjectProfileRepository {

    /** 项目画像 Mapper，用于保存和查询治理工作台的仓库画像。 */
    private final GovernanceProjectProfileMapper mapper;

    @Override
    public void save(GovernanceProjectProfile profile) {
        GovernanceProjectProfileDO dataObject = toDataObject(profile);
        if (dataObject.getId() == null || mapper.selectById(dataObject.getId()) == null) {
            mapper.insert(dataObject);
            return;
        }
        mapper.updateById(dataObject);
    }

    @Override
    public GovernanceProjectProfile findLatestByWorkspaceId(Long workspaceId) {
        if (workspaceId == null) {
            return null;
        }
        GovernanceProjectProfileDO dataObject = mapper.selectOne(new LambdaQueryWrapper<GovernanceProjectProfileDO>()
            .eq(GovernanceProjectProfileDO::getWorkspaceId, workspaceId)
            .eq(GovernanceProjectProfileDO::getDeleted, 0)
            .orderByDesc(GovernanceProjectProfileDO::getScannedAt)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public List<GovernanceProjectProfile> findRecent(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<GovernanceProjectProfileDO>()
                .eq(GovernanceProjectProfileDO::getDeleted, 0)
                .orderByDesc(GovernanceProjectProfileDO::getScannedAt)
                .last("limit " + Math.min(Math.max(limit, 1), 100)))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private GovernanceProjectProfileDO toDataObject(GovernanceProjectProfile profile) {
        GovernanceProjectProfileDO dataObject = new GovernanceProjectProfileDO();
        dataObject.setId(profile.getId());
        dataObject.setWorkspaceId(profile.getWorkspaceId());
        dataObject.setWorkspacePath(profile.getWorkspacePath());
        dataObject.setSummary(profile.getSummary());
        dataObject.setTechStackJson(profile.getTechStackJson());
        dataObject.setEntrypointsJson(profile.getEntrypointsJson());
        dataObject.setVerificationCommandsJson(profile.getVerificationCommandsJson());
        dataObject.setStatus(profile.getStatus());
        dataObject.setScannedAt(profile.getScannedAt());
        dataObject.setCreatedAt(profile.getCreatedAt());
        dataObject.setUpdatedAt(profile.getUpdatedAt());
        dataObject.setDeleted(profile.getDeleted());
        return dataObject;
    }

    private GovernanceProjectProfile toDomain(GovernanceProjectProfileDO dataObject) {
        return GovernanceProjectProfile.builder()
            .id(dataObject.getId())
            .workspaceId(dataObject.getWorkspaceId())
            .workspacePath(dataObject.getWorkspacePath())
            .summary(dataObject.getSummary())
            .techStackJson(dataObject.getTechStackJson())
            .entrypointsJson(dataObject.getEntrypointsJson())
            .verificationCommandsJson(dataObject.getVerificationCommandsJson())
            .status(dataObject.getStatus())
            .scannedAt(dataObject.getScannedAt())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
