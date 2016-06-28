package com.cloud.network.dao;

import com.cloud.network.NiciraNvpNicMappingVO;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;

import org.springframework.stereotype.Component;

@Component
public class NiciraNvpNicMappingDaoImpl extends GenericDaoBase<NiciraNvpNicMappingVO, Long> implements NiciraNvpNicMappingDao {

    protected final SearchBuilder<NiciraNvpNicMappingVO> nicSearch;

    public NiciraNvpNicMappingDaoImpl() {
        nicSearch = createSearchBuilder();
        nicSearch.and("nicUuid", nicSearch.entity().getNicUuid(), Op.EQ);
        nicSearch.done();
    }

    @Override
    public NiciraNvpNicMappingVO findByNicUuid(final String nicUuid) {
        final SearchCriteria<NiciraNvpNicMappingVO> sc = nicSearch.create();
        sc.setParameters("nicUuid", nicUuid);
        return findOneBy(sc);
    }
}
