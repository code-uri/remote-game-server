package aimlabs.gaming.rgs.roles;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class Role extends BaseDto {

    String uid;
    String name;
    List<String> permissions;

    public Role(String tenant,String account, String name){
        this.tenant = tenant;
        this.account = account;
        this.name = name;
    }
}