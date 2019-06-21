package com.pinyougou.seckill.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.pinyougou.pay.service.WeixinPayService;
import com.pinyougou.pojo.TbPayLog;
import com.pinyougou.pojo.TbSeckillGoods;
import com.pinyougou.pojo.TbSeckillOrder;
import com.pinyougou.seckill.service.SeckillOrderService;
import com.pinyougou.service.OrderService;
import entity.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sun.security.util.Cache;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制层
 * @author Administrator
 *
 */
@RestController
@RequestMapping("/pay")
public class PayController {
    @Reference
    private WeixinPayService weixinPayService;

    @Reference
    private OrderService orderService;

    @Reference
    private SeckillOrderService seckillOrderService;

    /**
     * 生成二维码
     * @return
     */

    @RequestMapping("/createNative")
    public Map createNative(){
        //获取当前用户
        String userId=SecurityContextHolder.getContext().getAuthentication().getName();
        //到redis查询秒杀订单
        TbSeckillOrder seckillOrder=seckillOrderService.searchOrderFromRedisByUserId(userId);

        //判断秒杀是否存在
        if (seckillOrder!=null){
            long fen =(long) (seckillOrder.getMoney().doubleValue()*100);
            return weixinPayService.createNative(seckillOrder.getId()+"",+fen+"");
        }else {
            return new HashMap();
        }

        //IdWorker idWorker=new IdWorker();
       // return weixinPayService.createNative(idWorker.nextId()+"","1");


    }

    /**
     * 查询支付状态
     * @param out_trade_no
     * @return
     */

    @RequestMapping("/queryPayStatus")
    public Result queryPayStatus(String out_trade_no){
        //获取当前用户
        String userId=SecurityContextHolder.getContext().getAuthentication().getName();

        Result result=null;
        int x=0;
        while(true){
            //调用查询接口
            Map<String,String> map = weixinPayService.queryPayStatus(out_trade_no);
            if (map==null){//出错
                result=new Result(false,"支付出错");
                break;
            }
            if(map.get("trade_code").equals("SUCCESS")){//如果成功
                result=new Result(true,"支付成功");
                //支付成功。从缓存持久化到mysql硬盘中
                seckillOrderService.saveOrderFromRedisToDb(userId,Long.valueOf(out_trade_no),map.get("transaction_id"));

                //修改订单状态
                //orderService.updateOrderStatus(out_trade_no,map.get("transation_id"));
                break;
            }
            try {
                Thread.sleep(3000);//间隔三秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //为了不让循环无休止地运行，我们定义一个循环变量，如果这个变量超过了这个值则退出循环，设置时间为5分钟
            x++;
            if (x>=100){
                result=new Result(false,"二维码超时");
                //1.调用微信的关闭订单接口
                Map<String,String> payresult=weixinPayService.closePay(out_trade_no);
                if (!"SUCCESS".equals(payresult.get("result_state"))){//如果返回结果是正常关闭
                    if ("ORDERPAID".equals(payresult.get("err_state"))){
                        result=new Result(true,"支付成功");
                        seckillOrderService.saveOrderFromRedisToDb(userId,Long.valueOf(out_trade_no),map.get("transaction_id"));

                    }
                }
                if(result.isSuccess()==false){
                    System.out.println("超时，取消订单");
                    //2.调用删除
                    seckillOrderService.deleteOrderFromRedis(userId,Long.valueOf(out_trade_no));
                }
                break;
            }

        }
        return result;
    }


}
