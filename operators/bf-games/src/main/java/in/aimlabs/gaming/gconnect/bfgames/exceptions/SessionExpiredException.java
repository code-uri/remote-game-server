package in.aimlabs.gaming.gconnect.bfgames.exceptions;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SessionExpiredException extends RuntimeException{

    public SessionExpiredException(String msg){
        super(msg);
    }
}
