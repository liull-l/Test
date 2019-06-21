package com.pinyougou.order.service.impl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.pinyougou.mapper.TbPayLogMapper;
import com.pinyougou.pojo.TbOrderItem;
import com.pinyougou.pojo.TbPayLog;
import com.pinyougou.pojogroup.Cart;
import com.pinyougou.service.OrderService;
import net.sf.jsqlparser.statement.replace.Replace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.dubbo.config.annotation.Service;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.pinyougou.mapper.TbOrderItemMapper;
import com.pinyougou.mapper.TbOrderMapper;
import com.pinyougou.pojo.TbOrder;
import com.pinyougou.pojo.TbOrderExample;
import com.pinyougou.pojo.TbOrderExample.Criteria;




import entity.PageResult;
import util.IdWorker;


/**
 * 服务实现层
 * @author Administrator
 *
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {

	@Autowired
	private TbOrderMapper orderMapper;
	
	/**
	 * 查询全部
	 */
	@Override
	public List<TbOrder> findAll() {
		return orderMapper.selectByExample(null);
	}

	/**
	 * 按分页查询
	 */
	@Override
	public PageResult findPage(int pageNum, int pageSize) {
		PageHelper.startPage(pageNum, pageSize);		
		Page<TbOrder> page=   (Page<TbOrder>) orderMapper.selectByExample(null);
		return new PageResult(page.getTotal(), page.getResult());
	}

	@Autowired
	private RedisTemplate<String,Object> redisTemplate;

	@Autowired
	private  TbOrderItemMapper orderItemMapper;

	@Autowired
	private IdWorker idWorker;
	@Autowired
	private TbPayLogMapper payLogMapper;

	/**
	 * 增加
	 */

	@Override
	public void add(TbOrder order) {
		//得到购物车数据
		List<Cart> cartList=(List<Cart>)
				redisTemplate.boundHashOps("cartList").get(order.getUserId());
		System.out.println(order.getUserId());

		List<String> orderIdList=new ArrayList();
		double total_money=0;//总金额
;		for (Cart cart:cartList){
			long orderId=idWorker.nextId();
			System.out.println("sellerId:"+cart.getSellerId());
			TbOrder tbOrder=new TbOrder();//新创建订单对象
			tbOrder.setOrderId(orderId);//订单ID
			tbOrder.setUserId(order.getUserId());//用户名
			tbOrder.setPaymentType(order.getPaymentType());//支付类型
			tbOrder.setStatus("1");//状态：未付款
			tbOrder.setCreateTime(new Date());//订单创建日期
			tbOrder.setUpdateTime(new Date());//订单更新日期
			tbOrder.setReceiverAreaName(order.getReceiverAreaName());//地址
			tbOrder.setReceiverMobile(order.getReceiverMobile());//手机号
			tbOrder.setReceiver(order.getReceiver());//收货人
			tbOrder.setSourceType(order.getSourceType());//订单来源
			tbOrder.setSellerId(cart.getSellerId());//商家ID
			//循环购物车明细
			double money=0;
			for (TbOrderItem orderItem:cart.getOrderItemList()){
				orderItem.setId(idWorker.nextId());
				orderItem.setOrderId(orderId);//订单ID
				orderItem.setSellerId(cart.getSellerId());
				money = orderItem.getTotalFee().doubleValue();//金额累计
				//新创建订单明细对象
				orderItemMapper.insert(orderItem);
			}
			tbOrder.setPayment(new BigDecimal(money));
			//新创建对订单对象
			orderMapper.insert(tbOrder);
			orderIdList.add(orderId+"");
			total_money+=money;

		}
		//添加支付日志
		if("1".equals(order.getPaymentType())){//如果是微信支付
			TbPayLog payLog=new TbPayLog();
			String outTradeNo=idWorker.nextId()+"";//支付订单号

			payLog.setOutTradeNo(idWorker.nextId()+"");//支付订单号
			payLog.setCreateTime(new Date());//创建时间
			//订单号列表，用逗号分隔
			String ids=orderIdList.toString().replace("[","").replace("]","").replace(" ","");
			payLog.setOrderList(ids);//订单号列表，逗号分隔

			payLog.setPayType("1");//支付类型
			payLog.setTotalFee((long)(total_money*100));//总金额(分)
			payLog.setTradeState("0");//支付状态
			payLog.setUserId(order.getUserId());//用户ID
			payLogMapper.insert(payLog);//插入到支付日志表
			redisTemplate.boundHashOps("payLog").put(order.getUserId(),payLog);//放入缓存
		}
		//删除购物车缓存
		redisTemplate.boundHashOps("cartList").delete(order.getUserId());

	}


	

	
	/**
	 * 修改
	 */
	@Override
	public void update(TbOrder order){
		orderMapper.updateByPrimaryKey(order);
	}	
	
	/**
	 * 根据ID获取实体
	 * @param id
	 * @return
	 */
	@Override
	public TbOrder findOne(Long id){
		return orderMapper.selectByPrimaryKey(id);
	}

	/**
	 * 批量删除
	 */
	@Override
	public void delete(Long[] ids) {
		for(Long id:ids){
			orderMapper.deleteByPrimaryKey(id);
		}		
	}
	
	
		@Override
	public PageResult findPage(TbOrder order, int pageNum, int pageSize) {
		PageHelper.startPage(pageNum, pageSize);
		
		TbOrderExample example=new TbOrderExample();
		Criteria criteria = example.createCriteria();
		
		if(order!=null){			
						if(order.getPaymentType()!=null && order.getPaymentType().length()>0){
				criteria.andPaymentTypeLike("%"+order.getPaymentType()+"%");
			}
			if(order.getPostFee()!=null && order.getPostFee().length()>0){
				criteria.andPostFeeLike("%"+order.getPostFee()+"%");
			}
			if(order.getStatus()!=null && order.getStatus().length()>0){
				criteria.andStatusLike("%"+order.getStatus()+"%");
			}
			if(order.getShippingName()!=null && order.getShippingName().length()>0){
				criteria.andShippingNameLike("%"+order.getShippingName()+"%");
			}
			if(order.getShippingCode()!=null && order.getShippingCode().length()>0){
				criteria.andShippingCodeLike("%"+order.getShippingCode()+"%");
			}
			if(order.getUserId()!=null && order.getUserId().length()>0){
				criteria.andUserIdLike("%"+order.getUserId()+"%");
			}
			if(order.getBuyerMessage()!=null && order.getBuyerMessage().length()>0){
				criteria.andBuyerMessageLike("%"+order.getBuyerMessage()+"%");
			}
			if(order.getBuyerNick()!=null && order.getBuyerNick().length()>0){
				criteria.andBuyerNickLike("%"+order.getBuyerNick()+"%");
			}
			if(order.getBuyerRate()!=null && order.getBuyerRate().length()>0){
				criteria.andBuyerRateLike("%"+order.getBuyerRate()+"%");
			}
			if(order.getReceiverAreaName()!=null && order.getReceiverAreaName().length()>0){
				criteria.andReceiverAreaNameLike("%"+order.getReceiverAreaName()+"%");
			}
			if(order.getReceiverMobile()!=null && order.getReceiverMobile().length()>0){
				criteria.andReceiverMobileLike("%"+order.getReceiverMobile()+"%");
			}
			if(order.getReceiverZipCode()!=null && order.getReceiverZipCode().length()>0){
				criteria.andReceiverZipCodeLike("%"+order.getReceiverZipCode()+"%");
			}
			if(order.getReceiver()!=null && order.getReceiver().length()>0){
				criteria.andReceiverLike("%"+order.getReceiver()+"%");
			}
			if(order.getInvoiceType()!=null && order.getInvoiceType().length()>0){
				criteria.andInvoiceTypeLike("%"+order.getInvoiceType()+"%");
			}
			if(order.getSourceType()!=null && order.getSourceType().length()>0){
				criteria.andSourceTypeLike("%"+order.getSourceType()+"%");
			}
			if(order.getSellerId()!=null && order.getSellerId().length()>0){
				criteria.andSellerIdLike("%"+order.getSellerId()+"%");
			}
	
		}
		
		Page<TbOrder> page= (Page<TbOrder>)orderMapper.selectByExample(example);		
		return new PageResult(page.getTotal(), page.getResult());
	}

	@Override
	public TbPayLog searchPayLogFromRedis(String userId) {
		return (TbPayLog) redisTemplate.boundHashOps("payLog").get(userId);
	}



	@Override
	public void updateOrderStatus(String out_trade_no, String transaction_id) {
		//修改支付日志状态
		TbPayLog payLog=payLogMapper.selectByPrimaryKey(out_trade_no);
		payLog.setPayTime(new Date());
		payLog.setTradeState("1");//已支付
		payLog.setTransactionId(transaction_id);//交易号
		payLogMapper.updateByPrimaryKey(payLog);
		//修改订单状态
		String orderList=payLog.getOrderList();//获取订单号列表
		String[] orderIds=orderList.split(",");//获取订单号数组

		for (String orderId:orderIds){
			TbOrder order=orderMapper.selectByPrimaryKey(Long.parseLong(orderId));
			if (order!=null){
				order.setStatus("2");//已付款
				orderMapper.updateByPrimaryKey(order);
			}
		}
		//清除reids缓存数据
		redisTemplate.boundHashOps("payLog").delete(payLog.getUserId());
	}


}
