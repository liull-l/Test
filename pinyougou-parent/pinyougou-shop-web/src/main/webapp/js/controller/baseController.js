//基本控制层
app.controller('baseController',function($scope){
    //重新加载列表 数据
    $scope.reloadList=function () {
        //切换页码
        $scope.search($scope.paginationConf.currentPage,$scope.paginationConf.itemsPerPage);
    }
    //分页控件配置
    $scope.paginationConf = {
        currentPage: 1,
        totalItems: 10,
        itemsPerPage: 10,
        perPageOptions: [10, 20, 30, 40, 50],
        onChange: function(){
            $scope.reloadList();
        }
    };
    $scope.selectIds=[];//用户勾选的ID集合

    //更新复选
    $scope.updateSelection=function($event,id){
        if($event.target.checked) {
            $scope.selectIds.push(id);//push的集合添加元素
        }else{
            var idx=$scope.selectIds.indexOf(id);//查找值得位置
            $scope.selectIds.splice(idx,1);//参数1：移除的位置  参数2：移除的个数
        }
    }

    //提取json字符串数据中某个属性，返回拼接字符串 逗号分隔
    $scope.jsonToString=function(jsonString,key){
        var json=JSON.parse(jsonString);//将json字符串转换为json对象
        var value="";
        for(var i=0;i<json.length;i++){
            if(i>0){
                value+=","
            }
            value+=json[i][key];
        }
        return value;
    }


    //从集合中按照key查询对象
    $scope.searchObjectByKey=function(list,key,keyValue){
        for(var i=0;i<list.length;i++){
            if(list[i][key]==keyValue){
                return list[i];
            }
        }
        return null;
    }


});