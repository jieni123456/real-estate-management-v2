package service;

import dao.ViewingDAO;
import model.Viewing;

import java.util.List;

public class ViewingService {

    private final ViewingDAO viewingDAO = new ViewingDAO();

    public List<Viewing> getAllViewings() {
        System.out.println("从DAO获取所有带看记录");
        return viewingDAO.getAll();
    }

    /** 单条带看记录；不存在时返回 null。编辑时的前置校验用（G-020） */
    public Viewing getById(long id) {
        return viewingDAO.findById(id);
    }

    public boolean insertViewing(Viewing viewing) {
        System.out.println("新增带看记录: 客户 " + viewing.getCustomerId()
                + " → 房屋 " + viewing.getHouseId());
        return viewingDAO.insert(viewing);
    }

    public boolean updateViewing(Viewing viewing) {
        System.out.println("更新带看记录: " + viewing.getId());
        return viewingDAO.update(viewing);
    }

    public boolean deleteViewing(long id) {
        System.out.println("删除带看记录: " + id);
        return viewingDAO.delete(id);
    }

    public int countByHouse(String houseId) {
        return viewingDAO.countByHouse(houseId);
    }

    public int countByCustomer(String customerId) {
        return viewingDAO.countByCustomer(customerId);
    }
}
