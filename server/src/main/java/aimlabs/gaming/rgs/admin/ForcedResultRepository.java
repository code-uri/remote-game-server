package aimlabs.gaming.rgs.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.QueryByExampleExecutor;

public interface ForcedResultRepository extends ListCrudRepository<ForcedResult, String>,
                PagingAndSortingRepository<ForcedResult, String>, QueryByExampleExecutor<ForcedResult> {

        Page<ForcedResult> findByGameId(String gameId, Pageable pageable);

        Page<ForcedResult> findBySession(String session, Pageable pageable);

        Page<ForcedResult> findByGamePlay(String gamePlay, Pageable pageable);

        Page<ForcedResult> findByGameIdAndSession(String gameId, String session, PageRequest pageRequest);

        Page<ForcedResult> findByGameIdAndSessionAndGamePlay(String gameId, String session, String gamePlay,
                        Pageable pageable);

        ForcedResult findFirstByGameIdAndSessionAndGamePlayOrderByIdAsc(String gameId, String session, String gamePlay);

        ForcedResult findFirstByGameIdAndSessionOrderByIdAsc(String gameId, String session);

        ForcedResult findFirstByGameIdOrderByIdAsc(String gameId);

        ForcedResult findFirst1ByGameIdAndSessionAndGamePlayOrderByIdDesc(String gameId, String session,
                        String gamePlay);

        ForcedResult findFirstByGameIdAndSessionOrderByIdDesc(String gameId, String session);

        ForcedResult findFirstByGameIdOrderByIdDesc(String gameId);

        ForcedResult findFirstByGameIdAndSessionAndJson_bonusOrderByIdAsc(String gameId, String session,
                        String playingBonus);

        ForcedResult findFirstByGameIdAndJson_bonusOrderByIdAsc(String gameId, String playingBonus);

        ForcedResult findFirstByGameIdAndSessionAndGamePlayAndJson_bonusOrderByIdAsc(String gameId, String session,
                        String gamePlay, String playingBonus);
}