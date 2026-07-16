package com.trade.market.dto;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TickDto implements Serializable {


    private String exchange;
    private String token;
    private String symbol;
    private double ltp;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private LocalDateTime timestamp;

    
    
   
    

  
    
   
    
 



							
					
							
							
				
}